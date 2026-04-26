package software.decibel.services.messaging;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.SetOptions;
import com.google.cloud.firestore.WriteBatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.dtos.messaging.ConversationPageResponse;
import software.decibel.dtos.messaging.ConversationResponse;
import software.decibel.dtos.messaging.MessagePageResponse;
import software.decibel.dtos.messaging.MessageResponse;
import software.decibel.dtos.messaging.SendMessageRequest;
import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.entities.User;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.UserRepository;
import software.decibel.services.notification.FcmNotificationService;
import software.decibel.services.notification.InAppNotificationService;
import software.decibel.services.user.UserService;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessagingService {

    private final ObjectProvider<Firestore> firestoreProvider;
    private final UserRepository userRepository;
    private final InAppNotificationService inAppNotificationService;
    private final UserService userService;

    private static final String CONVERSATIONS_COLLECTION = "conversations";
    private static final String MESSAGES_COLLECTION = "messages";
    private final FcmNotificationService fcmNotificationService;
    private final UserMapper userMapper;

    public ConversationResponse startConversation(Authentication authentication, Long recipientId) {
        Firestore firestore = firestoreProvider.getObject();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Long senderId = principal.getId();

        if (senderId.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot start a conversation with yourself");
        }

        User recipient = resolveActiveUser(recipientId, "Recipient not found");

        // Privacy check: private users cannot receive messages
        if (recipient.isPrivate()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is private and cannot receive messages");
        }

        // Block checks
        if (userService.hasBlocked(senderId, recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have blocked this user");
        }
        if (userService.hasBlocked(recipientId, senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user has blocked you");
        }

        String conversationId = getConversationId(senderId, recipientId);
        UserSummaryDTO sendToDto = userMapper.toUserSummaryDto(recipient);

        try {
            DocumentReference docRef = firestore.collection(CONVERSATIONS_COLLECTION).document(conversationId);
            DocumentSnapshot snapshot = docRef.get().get();

            if (!snapshot.exists()) {
                log.info("Creating new conversation: {}", conversationId);
                Map<String, Object> conversationData = new HashMap<>();
                conversationData.put("participants", Arrays.asList(senderId, recipientId));
                conversationData.put("lastMessage", "");
                conversationData.put("lastTimestamp", FieldValue.serverTimestamp());

                docRef.set(conversationData).get();

                return new ConversationResponse(
                        conversationId,
                        sendToDto,
                        "",
                        LocalDateTime.now(),
                        0L
                );
            } else {
                log.info("Conversation already exists: {}", conversationId);
                Timestamp ts = snapshot.getTimestamp("lastTimestamp");
                LocalDateTime ldt = ts != null
                        ? ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                        : LocalDateTime.now();

                Object participantsObj = snapshot.get("participants");
                List<Long> participants;
                if (participantsObj instanceof List<?>) {
                    participants = ((List<?>) participantsObj).stream()
                            .map(obj -> obj instanceof Long ? (Long) obj : Long.valueOf(obj.toString()))
                            .collect(Collectors.toList());
                } else {
                    participants = Arrays.asList(senderId, recipientId);
                }

                return new ConversationResponse(
                        conversationId,
                        sendToDto,
                        snapshot.getString("lastMessage"),
                        ldt,
                        0L
                );
            }
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error starting conversation in Firestore for conversationId: {}", conversationId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to start conversation: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error starting conversation in Firestore for conversationId: {}", conversationId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    public MessageResponse sendMessage(Authentication authentication, SendMessageRequest request) {
        Firestore firestore = firestoreProvider.getObject();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Long senderId = principal.getId();
        Long recipientId = request.recipientId();

        if (senderId.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot send a message to yourself");
        }

        User recipient = resolveActiveUser(recipientId, "Recipient not found");

        // Privacy check: private users cannot receive messages
        if (recipient.isPrivate()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is private and cannot receive messages");
        }

        // Block checks
        if (userService.hasBlocked(senderId, recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have blocked this user");
        }
        if (userService.hasBlocked(recipientId, senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user has blocked you");
        }

        String conversationId = getConversationId(senderId, recipientId);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", senderId);
        messageData.put("recipientId", recipientId);
        messageData.put("content", request.content());
        messageData.put("timestamp", FieldValue.serverTimestamp());
        messageData.put("isRead", false);

        try {
            DocumentReference docRef = firestore.collection(CONVERSATIONS_COLLECTION)
                    .document(conversationId)
                    .collection(MESSAGES_COLLECTION)
                    .add(messageData)
                    .get();

            // Update conversation metadata (last message, participants)
            Map<String, Object> conversationData = new HashMap<>();
            conversationData.put("participants", Arrays.asList(senderId, recipientId));
            conversationData.put("lastMessage", request.content());
            conversationData.put("lastTimestamp", FieldValue.serverTimestamp());

            firestore.collection(CONVERSATIONS_COLLECTION)
                    .document(conversationId)
                    .set(conversationData, SetOptions.merge());

            // Create notification
            inAppNotificationService.createNotification(
                    recipientId,
                    senderId,
                    NotificationType.REPLY,
                    ResourceType.USER,
                    senderId
            );
            User sender = resolveActiveUser(senderId, "Sender not found");

            fcmNotificationService.sendRealTimeChatMessage(
                    recipientId,
                    sender.getUsername(), // Or "User " + senderId if you don't have getUsername()
                    request.content()
            );
            UserSummaryDTO senderDto = userMapper.toUserSummaryDto(sender);

            return new MessageResponse(
                    docRef.getId(),
                    senderDto,
                    request.content(),
                    LocalDateTime.now(),
                    false
            );
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error sending message to Firestore for conversationId: {}", conversationId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to send message: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error sending message to Firestore for conversationId: {}", conversationId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    public MessagePageResponse getMessages(Authentication authentication, String conversationId, int page, int size) {
        Firestore firestore = firestoreProvider.getObject();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Long currentUserId = principal.getId();

        // 1. Security check: user must be a participant of the conversation
        // conversationId is "minId_maxId"
        String[] ids = conversationId.split("_");
        if (ids.length != 2) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation ID");
        }

        Long id1;
        Long id2;
        try {
            id1 = Long.parseLong(ids[0]);
            id2 = Long.parseLong(ids[1]);
            if (!currentUserId.equals(id1) && !currentUserId.equals(id2)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant of this conversation");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation ID format");
        }

        // 3. CACHE: Fetch both users once to prevent hitting the SQL database 20 times during the map loop
        Map<Long, UserSummaryDTO> userCache = new HashMap<>();
        try {

            User user1 = userService.getUserIfExistsById(id1);
            User user2 = userService.getUserIfExistsById(id2);
            userCache.put(id1, userMapper.toUserSummaryDto(user1));
            userCache.put(id2, userMapper.toUserSummaryDto(user2));
        } catch (Exception e) {
            log.error("Failed to load user summaries for conversation: {}", conversationId, e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to load participant data.");
        }

        // 4. Fetch the paginated messages from Firestore
        try {
            Query query = firestore.collection(CONVERSATIONS_COLLECTION)
                    .document(conversationId)
                    .collection(MESSAGES_COLLECTION)
                    .orderBy("timestamp", Query.Direction.DESCENDING)
                    .limit(size)
                    .offset(page * size);

            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();

            List<MessageResponse> messages = documents.stream()
                    .map(doc -> {
                        Timestamp ts = doc.getTimestamp("timestamp");
                        LocalDateTime ldt = ts != null
                                ? ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                : LocalDateTime.now();

                        // Extract senderId to get the cached UserSummaryDTO
                        Long senderId = doc.getLong("senderId");
                        UserSummaryDTO senderDto = userCache.get(senderId);

                        // Extract isRead safely (fallback to false if missing)
                        Boolean isReadRaw = doc.getBoolean("isRead");
                        boolean isRead = isReadRaw != null ? isReadRaw : false;
                        // Return the new DTO structure
                        return new MessageResponse(
                                doc.getId(),
                                senderDto,
                                doc.getString("content"),
                                ldt,
                                isRead
                        );
                    })
                    .collect(Collectors.toList());
            //Mark all unread messages as read
            markUnreadMessagesAsRead(conversationId, currentUserId);
            // 5. Calculate pagination elements
            long totalElements = 0;
            AggregateQuery countQuery = firestore.collection(CONVERSATIONS_COLLECTION)
                    .document(conversationId)
                    .collection(MESSAGES_COLLECTION)
                    .count();
            totalElements = countQuery.get().get().getCount();
            int totalPages = (int) Math.ceil((double) totalElements / size);

            return new MessagePageResponse(
                    messages,
                    page,
                    size,
                    totalElements,
                    totalPages,
                    page >= totalPages - 1
            );

        } catch (InterruptedException | ExecutionException e) {
            log.error("Error fetching messages from Firestore for conversationId: {}", conversationId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch messages: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching messages from Firestore for conversationId: {}", conversationId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    public ConversationPageResponse getConversations(Authentication authentication, int page, int size) {
        Firestore firestore = firestoreProvider.getObject();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Long currentUserId = principal.getId();

        try {
            Query query = firestore.collection(CONVERSATIONS_COLLECTION)
                    .whereArrayContains("participants", currentUserId)
                    .orderBy("lastTimestamp", Query.Direction.DESCENDING)
                    .limit(size)
                    .offset(page * size);

            ApiFuture<QuerySnapshot> querySnapshot = query.get();
            List<QueryDocumentSnapshot> documents = querySnapshot.get().getDocuments();

            List<ConversationResponse> conversations = documents.stream()
                    .map(doc -> {
                        Timestamp ts = doc.getTimestamp("lastTimestamp");
                        LocalDateTime ldt = ts != null
                                ? ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()
                                : LocalDateTime.now();

                        Object participantsObj = doc.get("participants");
                        List<Long> participants;
                        if (participantsObj instanceof List<?>) {
                            participants = ((List<?>) participantsObj).stream()
                                    .map(obj -> obj instanceof Long ? (Long) obj : Long.valueOf(obj.toString()))
                                    .collect(Collectors.toList());
                        } else {
                            participants = Collections.emptyList();
                        }
                        long unreadCount = 0;
                        // Count unread messages for this specific conversation
                        try {
                            AggregateQuery countQuery = firestore.collection("conversations")
                                    .document(doc.getId())
                                    .collection("messages")
                                    .whereEqualTo("recipientId", currentUserId) // Only count messages sent TO current user
                                    .whereEqualTo("isRead", false) // Only count unread
                                    .count();

                            // Note: .get().get() resolves the ApiFuture
                            unreadCount = countQuery.get().get().getCount();
                        } catch (Exception e) {
                            log.error("Failed to fetch unread count for conversation: {}", doc.getId(), e);
                            System.err.println("\n\n🔥 FIRESTORE INDEX URL: " + e.getMessage() + "\n\n");
                        }
                        Long otherUserId = participants.stream()
                                .filter(id -> !id.equals(currentUserId))
                                .findFirst()
                                .orElse(currentUserId);
                        User otherUserEntity = userService.getUserIfExistsById(otherUserId);
                        UserSummaryDTO sendToDto = userMapper.toUserSummaryDto(otherUserEntity);

                        return new ConversationResponse(
                                doc.getId(),
                                sendToDto,
                                doc.getString("lastMessage"),
                                ldt,
                                unreadCount
                        );
                    })
                    .collect(Collectors.toList());

            AggregateQuery countQuery = firestore.collection(CONVERSATIONS_COLLECTION)
                    .whereArrayContains("participants", currentUserId)
                    .count();
            long totalElements = countQuery.get().get().getCount();
            int totalPages = size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);

            return new ConversationPageResponse(
                    conversations,
                    page,
                    size,
                    totalElements,
                    totalPages,
                    page >= totalPages - 1
            );
        } catch (InterruptedException | ExecutionException e) {
            log.error("Error fetching conversations from Firestore for user: {}", currentUserId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to fetch conversations: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error fetching conversations from Firestore for user: {}", currentUserId, e);
            String message = e.getMessage();
            if (message != null) {
                if (message.contains("Cloud Firestore API has not been used")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "Messaging service is currently unavailable. Please contact support to enable the Firestore API.");
                }
                if (message.contains("The query requires an index")) {
                    throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                            "The messaging service is currently setting up database indexes. This may take a few minutes. Please check the Firestore console if the issue persists.");
                }
            }
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error: " + e.getMessage());
        }
    }

    private void markUnreadMessagesAsRead(String conversationId, Long currentUserId) {
        try {
            Firestore firestore = firestoreProvider.getObject();
            CollectionReference messagesRef = firestore.collection("conversations")
                    .document(conversationId)
                    .collection("messages");

            // Find all messages sent TO the current user that are NOT read
            ApiFuture<QuerySnapshot> future = messagesRef
                    .whereEqualTo("recipientId", currentUserId)
                    .whereEqualTo("isRead", false)
                    .get();

            List<QueryDocumentSnapshot> unreadDocs = future.get().getDocuments();

            if (!unreadDocs.isEmpty()) {
                WriteBatch batch = firestore.batch();
                for (DocumentSnapshot doc : unreadDocs) {
                    batch.update(doc.getReference(), "isRead", true);
                }
                // Commit the batch update asynchronously so it doesn't slow down the GET request
                batch.commit();
            }
        } catch (Exception e) {
            log.error("Failed to mark messages as read for conversation: {}", conversationId, e);
            System.err.println("\n\n🔥 FIRESTORE INDEX URL: " + e.getMessage() + "\n\n");
        }
    }

    private String getConversationId(Long id1, Long id2) {
        if (id1 < id2) {
            return id1 + "_" + id2;
        } else {
            return id2 + "_" + id1;
        }
    }

    private User resolveActiveUser(Long userId, String message) {
        try {
            return userService.getUserIfExistsById(userId);
        } catch (software.decibel.exceptions.custom.ResourceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, message);
        }
    }
}
