package software.decibel.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.*;
import org.mockito.*;
import org.springframework.data.domain.*;
import software.decibel.dtos.comment.*;
import software.decibel.dtos.comment.replies.*;
import software.decibel.entities.*;
import software.decibel.exceptions.custom.*;
import software.decibel.mappers.CommentMapper;
import software.decibel.repositories.CommentRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserService;
import software.decibel.utils.TrackChecksUtil;

class CommentServiceTest {

    private final Long mockUserId = 1L;
    @Mock
    private CommentRepository commentRepository;
    @Mock
    private UserService userService;
    @Mock
    private TrackService trackService;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private TrackRepository trackRepository;
    @Mock
    private TrackChecksUtil trackChecksUtil;
    @InjectMocks
    private CommentService commentService;
    private MockedStatic<JwtService> jwtMock;

    @BeforeEach
    void setup() {
        MockitoAnnotations.openMocks(this);

        // we will mock jwt once here so that anytime a test or service needs to use it
        // to get user id
        // we automatically get user id 1
        jwtMock = mockStatic(JwtService.class);
        jwtMock.when(JwtService::getCurrentUserId).thenReturn(mockUserId);
    }

    @AfterEach
    void cleanup() {
        jwtMock.close();
    }

    // addComment
    // --------------------------------
    @Test
    void addComment_shouldSaveComment_whenEverythingIsValid() {
        // Arrange
        // valid timestamp <= duration
        Long trackId = 1L;
        CreateCommentRequest request = new CreateCommentRequest("asdfasd", 30);
        User user = new User();
        Track track = new Track();
        track.setDurationSeconds(60);
        Comment commentEntity = new Comment();

        CommentResponse commentResponse = mock(CommentResponse.class);

        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
        when(trackChecksUtil.getTrackIfExistsById(trackId)).thenReturn(track);
        when(commentMapper.toEntity(request, user, track)).thenReturn(commentEntity);
        when(commentRepository.save(commentEntity)).thenReturn(commentEntity);
        when(commentMapper.toCommentResponse(commentEntity)).thenReturn(commentResponse);

        // Act
        CommentResponse response = commentService.addComment(trackId, request);

        // Assert
        assertThat(response).isEqualTo(commentResponse);
        verify(commentRepository).save(commentEntity);
    }

    @Test
    void addComment_shouldThrowInvalidTimestamp_whenTimestampTooHigh() {
        // Arrange
        // invalid timestamp > duration
        Long trackId = 1L;
        CreateCommentRequest request = new CreateCommentRequest("nice track", 100);
        Track track = new Track();
        track.setDurationSeconds(60);

        when(userService.getUserIfExistsById(mockUserId)).thenReturn(new User());
        when(trackChecksUtil.getTrackIfExistsById(trackId)).thenReturn(track);

        // Act & Assert
        assertThrows(
                InvalidTimestampException.class, () -> commentService.addComment(trackId, request));
    }

    // getTrackComments
    // --------------------------------
    @Test
    void getTrackComments_shouldReturnPageResponse() {
        // Arrange
        Long trackId = 1L;
        int page = 0, size = 10;
        Track track = new Track();
        Page<Comment> pageResult = new PageImpl<>(List.of(new Comment()));
        CommentPageResponse pageResponse = mock(CommentPageResponse.class);
        ReplyPageResponse replyPageResponse = mock(ReplyPageResponse.class);

        when(trackChecksUtil.getTrackIfExistsById(trackId)).thenReturn(track);
        when(commentRepository.findByTrackId(eq(trackId), any(PageRequest.class)))
                .thenReturn(pageResult);
        when(commentMapper.toPageResponse(pageResult)).thenReturn(pageResponse);
        when(commentMapper.toReplyPageResponse(pageResult)).thenReturn(replyPageResponse);

        // Act
        CommentPageResponse response = commentService.getTrackComments(trackId, page, size);

        // Assert
        assertThat(response).isNotNull();
        verify(commentRepository).findByTrackId(eq(trackId), any(PageRequest.class));
    }

    // addReply
    // --------------------------------
    @Test
    void addReply_shouldSaveReply_whenParentCommentValid() {
        // Arrange
        Long commentId = 1L;
        CreateCommentRequest request = new CreateCommentRequest("replying", null);
        User user = new User();
        Track track = new Track();
        Comment parentComment = new Comment();
        parentComment.setTrack(track);
        Comment replyEntity = new Comment();
        replyEntity.setTrack(track);
        ReplyResponse replyResponse = mock(ReplyResponse.class);

        when(userService.getUserIfExistsById(mockUserId)).thenReturn(user);
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(parentComment));
        when(commentMapper.toReplyEntity(request, user, track, parentComment)).thenReturn(replyEntity);
        when(commentRepository.save(replyEntity)).thenReturn(replyEntity);
        when(commentMapper.toReplyResponse(replyEntity)).thenReturn(replyResponse);

        // Act
        ReplyResponse response = commentService.addReply(commentId, request);

        // Assert
        assertThat(response).isEqualTo(replyResponse);
        verify(commentRepository).save(replyEntity);
    }

    @Test
    void addReply_shouldThrowReplyToReplyNotAllowed_ifParentIsReply() {
        // Arrange
        Comment parentReply = new Comment();
        parentReply.setParentComment(new Comment());
        Long commentId = 1L;
        when(userService.getUserIfExistsById(mockUserId)).thenReturn(new User());
        when(commentRepository.findById(commentId)).thenReturn(Optional.of(parentReply));

        // Act & Assert
        assertThrows(
                ReplyToReplyNotAllowedException.class,
                () -> commentService.addReply(commentId, new CreateCommentRequest("text", null)));
    }

    // getReplies
    // --------------------------------
    @Test
    void getReplies_shouldReturnRepliesPage() {
        // Arrange
        Long commentId = 1L;
        Comment parentComment = new Comment();
        Page<Comment> pageResult = new PageImpl<>(List.of(new Comment()));
        ReplyPageResponse replyPageResponse = mock(ReplyPageResponse.class);

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(parentComment));
        when(commentRepository.findByParentCommentId(eq(commentId), any(PageRequest.class)))
                .thenReturn(pageResult);
        when(commentMapper.toReplyPageResponse(pageResult)).thenReturn(replyPageResponse);

        // Act
        ReplyPageResponse response = commentService.getReplies(commentId, 0, 10);

        // Assert
        assertThat(response).isNotNull();
        verify(commentRepository).findByParentCommentId(eq(commentId), any(PageRequest.class));
    }

    @Test
    void getReplies_shouldThrowReplyToReplyNotAllowed_ifCommentIsReply() {
        // Arrange
        Comment replyComment = new Comment();
        replyComment.setParentComment(new Comment());
        when(commentRepository.findById(1L)).thenReturn(Optional.of(replyComment));

        // Act & Assert
        assertThrows(ReplyToReplyNotAllowedException.class, () -> commentService.getReplies(1L, 0, 10));
    }

    // getCommentIfExistsById
    // --------------------------------
    @Test
    void getCommentIfExistsById_shouldReturnComment_whenFound() {
        // Arrange
        Comment comment = new Comment();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        // Act
        Comment result = commentService.getCommentIfExistsById(1L);

        // Assert
        assertThat(result).isEqualTo(comment);
    }

    @Test
    void getCommentIfExistsById_shouldThrow_whenNotFound() {
        // Arrange
        when(commentRepository.findById(1L)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(ResourceNotFoundException.class, () -> commentService.getCommentIfExistsById(1L));
    }
}
