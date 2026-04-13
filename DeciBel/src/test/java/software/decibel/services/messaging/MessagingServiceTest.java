package software.decibel.services.messaging;

import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.WriteResult;
import com.google.api.core.ApiFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.dtos.messaging.ConversationPageResponse;
import software.decibel.dtos.messaging.ConversationResponse;
import software.decibel.dtos.messaging.MessageResponse;
import software.decibel.dtos.messaging.SendMessageRequest;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.notification.InAppNotificationService;

import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.AggregateQuery;
import com.google.cloud.firestore.AggregateQuerySnapshot;
import org.springframework.beans.factory.ObjectProvider;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

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
    private Authentication authentication;

    @InjectMocks
    private MessagingService messagingService;

    private UserPrincipal senderPrincipal;
    private User recipient;

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
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        
        SendMessageRequest request = new SendMessageRequest(2L, "hello");

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, 
                () -> messagingService.sendMessage(authentication, request));
        
        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("This user is private and cannot receive messages", exception.getReason());
    }

    @Test
    void sendMessage_blockedBySender_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
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
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
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
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(1L, 2L)).thenReturn(false);
        when(blockRepository.existsByBlocker_IdAndBlocked_Id(2L, 1L)).thenReturn(false);

        SendMessageRequest request = new SendMessageRequest(2L, "hello");

        // Mock Firestore
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

        verify(inAppNotificationService).createNotification(
                eq(2L),
                eq(1L),
                eq(NotificationType.REPLY),
                eq(ResourceType.USER),
                eq(1L)
        );
    }

    @Test
    void startConversation_toPrivateUser_throwsForbidden() {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        recipient.setPrivate(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> messagingService.startConversation(authentication, 2L));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        assertEquals("This user is private and cannot receive messages", exception.getReason());
    }

    @Test
    void startConversation_success_returnsConversation() throws ExecutionException, InterruptedException {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);
        when(userRepository.findById(2L)).thenReturn(Optional.of(recipient));
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
        assertEquals(2, response.participants().size());
        verify(conversationDoc).set(anyMap());
    }

    @Test
    void getConversations_success() throws ExecutionException, InterruptedException {
        when(authentication.getPrincipal()).thenReturn(senderPrincipal);

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

        when(conversations.whereArrayContains("participants", 1L)).thenReturn(query); // Re-called for count
        when(query.count()).thenReturn(aggregateQuery);
        when(aggregateQuery.get()).thenReturn(aggregateQuerySnapshotFuture);
        when(aggregateQuerySnapshotFuture.get()).thenReturn(aggregateQuerySnapshot);
        when(aggregateQuerySnapshot.getCount()).thenReturn(1L);

        ConversationPageResponse response = messagingService.getConversations(authentication, 0, 10);

        assertEquals(1, response.getContent().size());
        assertEquals("1_2", response.getContent().get(0).id());
        assertEquals(1L, response.getTotalElements());
    }
}
