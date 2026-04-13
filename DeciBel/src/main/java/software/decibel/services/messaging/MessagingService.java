package software.decibel.services.messaging;

import com.google.api.core.ApiFuture;
import com.google.cloud.Timestamp;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.dtos.messaging.ConversationPageResponse;
import software.decibel.dtos.messaging.ConversationResponse;
import software.decibel.dtos.messaging.MessagePageResponse;
import software.decibel.dtos.messaging.MessageResponse;
import software.decibel.dtos.messaging.SendMessageRequest;
import software.decibel.entities.User;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.notification.InAppNotificationService;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class MessagingService {

    private final ObjectProvider<Firestore> firestoreProvider;
    private final UserRepository userRepository;
    private final BlockRepository blockRepository;
    private final InAppNotificationService inAppNotificationService;

    private static final String CONVERSATIONS_COLLECTION = "conversations";
    private static final String MESSAGES_COLLECTION = "messages";

    public ConversationResponse startConversation(Authentication authentication, Long recipientId) {
        Firestore firestore = firestoreProvider.getObject();
        UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
        Long senderId = principal.getId();

        if (senderId.equals(recipientId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot start a conversation with yourself");
        }

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));

        // Privacy check: private users cannot receive messages
        if (recipient.isPrivate()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is private and cannot receive messages");
        }

        // Block checks
        if (blockRepository.existsByBlocker_IdAndBlocked_Id(senderId, recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have blocked this user");
        }
        if (blockRepository.existsByBlocker_IdAndBlocked_Id(recipientId, senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user has blocked you");
        }

        String conversationId = getConversationId(senderId, recipientId);

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
                        Arrays.asList(senderId, recipientId),
                        "",
                        LocalDateTime.now()
                );
            } else {
                log.info("Conversation already exists: {}", conversationId);
                Timestamp ts = snapshot.getTimestamp("lastTimestamp");
                LocalDateTime ldt = ts != null ? 
                        ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : 
                        LocalDateTime.now();
                
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
                        participants,
                        snapshot.getString("lastMessage"),
                        ldt
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

        User recipient = userRepository.findById(recipientId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Recipient not found"));

        // Privacy check: private users cannot receive messages
        if (recipient.isPrivate()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user is private and cannot receive messages");
        }

        // Block checks
        if (blockRepository.existsByBlocker_IdAndBlocked_Id(senderId, recipientId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You have blocked this user");
        }
        if (blockRepository.existsByBlocker_IdAndBlocked_Id(recipientId, senderId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This user has blocked you");
        }

        String conversationId = getConversationId(senderId, recipientId);

        Map<String, Object> messageData = new HashMap<>();
        messageData.put("senderId", senderId);
        messageData.put("recipientId", recipientId);
        messageData.put("content", request.content());
        messageData.put("timestamp", FieldValue.serverTimestamp());

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

            return new MessageResponse(
                    docRef.getId(),
                    senderId,
                    recipientId,
                    request.content(),
                    LocalDateTime.now()
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

        // Security check: user must be a participant of the conversation
        // conversationId is "minId_maxId"
        String[] ids = conversationId.split("_");
        if (ids.length != 2) {
             throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation ID");
        }
        try {
            Long id1 = Long.parseLong(ids[0]);
            Long id2 = Long.parseLong(ids[1]);
            if (!currentUserId.equals(id1) && !currentUserId.equals(id2)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "You are not a participant of this conversation");
            }
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid conversation ID format");
        }

        try {
            // Firestore pagination is usually cursor-based, but we'll use offset for simplicity if size is small,
            // or just fetch with limit if we want simple pagination.
            // Requirement says "paginated response of the chat".
            
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
                        LocalDateTime ldt = ts != null ? 
                                ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() : 
                                LocalDateTime.now();
                        return new MessageResponse(
                                doc.getId(),
                                doc.getLong("senderId"),
                                doc.getLong("recipientId"),
                                doc.getString("content"),
                                ldt
                        );
                    })
                    .collect(Collectors.toList());

            // For total elements, we might need another query or just use the count from metadata if we stored it
            // For now, let's just provide what we have. 
            // Better to use Firestore's aggregation for count if needed.
            long totalElements = 0; // Simple placeholder or actual count
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
                        LocalDateTime ldt = ts != null ?
                                ts.toDate().toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime() :
                                LocalDateTime.now();

                        Object participantsObj = doc.get("participants");
                        List<Long> participants;
                        if (participantsObj instanceof List<?>) {
                            participants = ((List<?>) participantsObj).stream()
                                    .map(obj -> obj instanceof Long ? (Long) obj : Long.valueOf(obj.toString()))
                                    .collect(Collectors.toList());
                        } else {
                            participants = Collections.emptyList();
                        }

                        return new ConversationResponse(
                                doc.getId(),
                                participants,
                                doc.getString("lastMessage"),
                                ldt
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

    private String getConversationId(Long id1, Long id2) {
        if (id1 < id2) {
            return id1 + "_" + id2;
        } else {
            return id2 + "_" + id1;
        }
    }
}
