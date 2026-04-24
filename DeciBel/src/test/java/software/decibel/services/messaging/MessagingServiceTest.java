package software.decibel.services.messaging;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;

import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.dtos.messaging.ConversationPageResponse;
import software.decibel.dtos.messaging.ConversationResponse;
import software.decibel.dtos.messaging.MessageResponse;
import software.decibel.dtos.messaging.SendMessageRequest;
import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.notification.FcmNotificationService;
import software.decibel.services.notification.InAppNotificationService;
import software.decibel.services.user.UserService;

@ExtendWith(MockitoExtension.class)
class MessagingServiceTest {

    @Mock
    private Firestore firestore;
    @Mock
    private ObjectProvider<Firestore> firestoreProvider;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BlockRepository blockRepository;
    @Mock
    private InAppNotificationService inAppNotificationService;
    @Mock
    private UserService userService;
    @Mock
    private Authentication authentication;
    @Mock
    private FcmNotificationService fcmNotificationService;
    @Mock
    private UserMapper userMapper; // Added UserMapper mock

    @InjectMocks
    private MessagingService messagingService;

    private UserPrincipal senderPrincipal;
    private User recipient;
    private UserSummaryDTO senderSummary;
    private UserSummaryDTO recipientSummary;

    @BeforeEach
    void setUp() {
        lenient().when(firestoreProvider.getObject()).thenReturn(firestore);

        senderPrincipal = UserPrincipal.builder()
                .id(1L)
                .username("sender")
                .tier(AccountTier.FREE)
                .build();

        recipient = User.builder()
                .id(2L)
                .username("recipient")
                .isPrivate(false)
                .build();

        // Setup mock DTOs for mapping
        senderSummary = new UserSummaryDTO(1L, "sender", "Sender Display", null, false, 0, 0);
        recipientSummary = new UserSummaryDTO(2L, "recipient", "Recipient Display", null, false, 0, 0);
    }

    @Test
    void sendMessage_toSelf_throwsBadRequest() {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        SendMessageRequest request = new SendMessageRequest(1L, "hello");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messagingService.sendMessage(authentication, request));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertEquals("You cannot send a message to yourself", exception.getReason());
    }

    @Test
    void sendMessage_toPrivateUser_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        recipient.setPrivate(true);
        when(userService.getUserIfExistsById(2L)).thenReturn(recipient);

        SendMessageRequest request = new SendMessageRequest(2L, "hello");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messagingService.sendMessage(authentication, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("This user is private and cannot receive messages", exception.getReason());
    }

    @Test
    void sendMessage_blockedBySender_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        when(userService.getUserIfExistsById(2L)).thenReturn(recipient);
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(1L, 2L)).thenReturn(true);

        SendMessageRequest request = new SendMessageRequest(2L, "hello");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messagingService.sendMessage(authentication, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("You have blocked this user", exception.getReason());
    }

    @Test
    void sendMessage_blockedByRecipient_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        when(userService.getUserIfExistsById(2L)).thenReturn(recipient);
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(1L, 2L)).thenReturn(false);
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(2L, 1L)).thenReturn(true);

        SendMessageRequest request = new SendMessageRequest(2L, "hello");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messagingService.sendMessage(authentication, request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("This user has blocked you", exception.getReason());
    }

    @Test
    void sendMessage_success_callsNotification() throws ExecutionException, InterruptedException {
        Long senderId = 1L;
        User mockSender = mock(User.class);

        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        when(userService.getUserIfExistsById(2L)).thenReturn(recipient);
        when(userService.getUserIfExistsById(senderId)).thenReturn(mockSender);

        when(mockSender.getUsername()).thenReturn("testuser");

        // Mock the UserMapper call
        when(userMapper.toUserSummaryDTO(mockSender)).thenReturn(senderSummary);

        when(blockRepository.existsByBlocker_IdAndBlocked_Id(senderId, 2L)).thenReturn(false);
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(2L, senderId)).thenReturn(false);

        SendMessageRequest request = new SendMessageRequest(2L, "hello");

        CollectionReference conversations = mock(CollectionReference.class);
        DocumentReference conversationDoc = mock(DocumentReference.class);
        CollectionReference messages = mock(CollectionReference.class);
        ApiFuture<DocumentReference> addFuture = mock(ApiFuture.class);
        DocumentReference messageDoc = mock(DocumentReference.class);
        ApiFuture<WriteResult> setFuture = mock(ApiFuture.class);

        when(firestore.collection("conversations")).thenReturn(conversations);
        when(conversations.document(anyString())).thenReturn(conversationDoc);
        when(conversationDoc.collection("messages")).thenReturn(messages);
        when(messages.add(anyMap())).thenReturn(addFuture);
        when(addFuture.get()).thenReturn(messageDoc);
        when(messageDoc.getId()).thenReturn("msgId");
        when(conversationDoc.set(anyMap(), any())).thenReturn(setFuture);

        MessageResponse response = messagingService.sendMessage(authentication, request);

        assertEquals("msgId", response.id());
        assertEquals("hello", response.content());
        assertEquals(1L, response.senderDto().id());
        verify(inAppNotificationService).createNotification(
                eq(2L),
                eq(senderId),
                eq(NotificationType.REPLY),
                eq(ResourceType.USER),
                eq(senderId)
        );

        verify(fcmNotificationService).sendRealTimeChatMessage(
                eq(2L),
                eq("testuser"),
                eq("hello")
        );
    }

    @Test
    void startConversation_toPrivateUser_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        lenient().when(userService.getUserIfExistsById(1L)).thenReturn(User.builder().id(1L).build());
        recipient.setPrivate(true);
        when(userService.getUserIfExistsById(2L)).thenReturn(recipient);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messagingService.startConversation(authentication, 2L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("This user is private and cannot receive messages", exception.getReason());
    }

    @Test
    void startConversation_success_returnsConversation() throws ExecutionException, InterruptedException {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);

        User senderUser = User.builder().id(1L).build();

        lenient().when(userService.getUserIfExistsById(1L)).thenReturn(senderUser);

        lenient().when(userMapper.toUserSummaryDTO(senderUser)).thenReturn(senderSummary);

        when(userService.getUserIfExistsById(2L)).thenReturn(recipient);
        when(userMapper.toUserSummaryDTO(recipient)).thenReturn(recipientSummary);

        when(blockRepository.existsByBlocker_IdAndBlocked_Id(1L, 2L)).thenReturn(false);
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(2L, 1L)).thenReturn(false);

        CollectionReference conversations = mock(CollectionReference.class);
        DocumentReference conversationDoc = mock(DocumentReference.class);
        ApiFuture<DocumentSnapshot> getFuture = mock(ApiFuture.class);
        DocumentSnapshot snapshot = mock(DocumentSnapshot.class);
        ApiFuture<WriteResult> setFuture = mock(ApiFuture.class);

        when(firestore.collection("conversations")).thenReturn(conversations);
        when(conversations.document("1_2")).thenReturn(conversationDoc);
        when(conversationDoc.get()).thenReturn(getFuture);
        when(getFuture.get()).thenReturn(snapshot);
        when(snapshot.exists()).thenReturn(false);
        when(conversationDoc.set(anyMap())).thenReturn(setFuture);

        ConversationResponse response = messagingService.startConversation(authentication, 2L);

        assertEquals("1_2", response.id());
        assertEquals(2L, response.senderDto().id());
        assertEquals(0L, response.unreadCount());
        verify(conversationDoc).set(anyMap());
    }

    @Test
    void getConversations_success() throws ExecutionException, InterruptedException {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);

        when(userService.getUserIfExistsById(2L)).thenReturn(recipient);
        when(userMapper.toUserSummaryDTO(recipient)).thenReturn(recipientSummary);

        CollectionReference conversations = mock(CollectionReference.class);
        Query query = mock(Query.class);
        ApiFuture<QuerySnapshot> querySnapshotFuture = mock(ApiFuture.class);
        QuerySnapshot querySnapshot = mock(QuerySnapshot.class);
        QueryDocumentSnapshot doc1 = mock(QueryDocumentSnapshot.class);
        AggregateQuery aggregateQuery = mock(AggregateQuery.class);
        ApiFuture<AggregateQuerySnapshot> aggregateQuerySnapshotFuture = mock(ApiFuture.class);
        AggregateQuerySnapshot aggregateQuerySnapshot = mock(AggregateQuerySnapshot.class);

        when(firestore.collection("conversations")).thenReturn(conversations);
        when(conversations.whereArrayContains("participants", 1L)).thenReturn(query);
        when(query.orderBy(anyString(), any())).thenReturn(query);
        when(query.limit(anyInt())).thenReturn(query);
        when(query.offset(anyInt())).thenReturn(query);
        when(query.get()).thenReturn(querySnapshotFuture);
        when(querySnapshotFuture.get()).thenReturn(querySnapshot);
        when(querySnapshot.getDocuments()).thenReturn(Collections.singletonList(doc1));

        when(doc1.getId()).thenReturn("1_2");
        when(doc1.get("participants")).thenReturn(Arrays.asList(1L, 2L));
        when(doc1.getString("lastMessage")).thenReturn("hello");
        when(doc1.getTimestamp("lastTimestamp")).thenReturn(null);

        when(conversations.whereArrayContains("participants", 1L)).thenReturn(query);
        when(query.count()).thenReturn(aggregateQuery);
        when(aggregateQuery.get()).thenReturn(aggregateQuerySnapshotFuture);
        when(aggregateQuerySnapshotFuture.get()).thenReturn(aggregateQuerySnapshot);
        when(aggregateQuerySnapshot.getCount()).thenReturn(1L);

        // Mocking the inner unread count query
        DocumentReference docRef = mock(DocumentReference.class);
        CollectionReference messagesRef = mock(CollectionReference.class);
        Query unreadQ1 = mock(Query.class);
        Query unreadQ2 = mock(Query.class);
        AggregateQuery unreadAgg = mock(AggregateQuery.class);
        ApiFuture<AggregateQuerySnapshot> unreadAggFut = mock(ApiFuture.class);
        AggregateQuerySnapshot unreadSnap = mock(AggregateQuerySnapshot.class);

        when(conversations.document("1_2")).thenReturn(docRef);
        when(docRef.collection("messages")).thenReturn(messagesRef);
        when(messagesRef.whereEqualTo("recipientId", 1L)).thenReturn(unreadQ1);
        when(unreadQ1.whereEqualTo("isRead", false)).thenReturn(unreadQ2);
        when(unreadQ2.count()).thenReturn(unreadAgg);
        when(unreadAgg.get()).thenReturn(unreadAggFut);
        when(unreadAggFut.get()).thenReturn(unreadSnap);
        when(unreadSnap.getCount()).thenReturn(5L);
        ConversationPageResponse response = messagingService.getConversations(authentication, 0, 10);

        assertEquals(1, response.content().size());
        assertEquals("1_2", response.content().get(0).id());
        assertEquals(1L, response.totalElements());
        assertEquals(2L, response.content().get(0).senderDto().id());
        assertEquals(5L, response.content().get(0).unreadCount());
    }
}
