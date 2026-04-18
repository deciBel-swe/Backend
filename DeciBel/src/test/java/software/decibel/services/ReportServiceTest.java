package software.decibel.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.dtos.moderation.ReportRequest;
import software.decibel.entities.Comment;
import software.decibel.entities.Report;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.ReportTargetType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.ReportSubmissionMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserService;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserService userService;
    @Mock
    private TrackService trackService;
    @Mock
    private CommentService commentService;
    @Mock
    private ReportSubmissionMapper reportSubmissionMapper;

    @InjectMocks
    private ReportService reportService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void reportTrack_whenRequestIsValid_savesTrackReportAndReturnsMessage() {
        setAuthenticatedUser(7L);
        ReportRequest request = new ReportRequest("Spam", "Misleading metadata");
        User user = User.builder().id(7L).username("listener").tier(AccountTier.FREE).build();
        Track track = Track.builder().id(15L).build();
        MessageResponse mapperResponse = new MessageResponse("Track reported successfully");
        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);

        when(userService.getUserIfExistsById(7L)).thenReturn(user);
        when(trackService.getTrackIfExistsById(15L)).thenReturn(track);
        when(reportSubmissionMapper.toTrackReportSubmittedResponse()).thenReturn(mapperResponse);

        MessageResponse response = reportService.reportTrack(15L, request);

        assertEquals("Track reported successfully", response.message());
        verify(reportRepository).save(reportCaptor.capture());
        Report savedReport = reportCaptor.getValue();
        assertEquals(7L, savedReport.getReporterId());
        assertEquals(15L, savedReport.getTargetId());
        assertEquals(ReportTargetType.TRACK, savedReport.getTargetType());
        assertEquals("Spam", savedReport.getReason());
        assertEquals("Misleading metadata", savedReport.getDescription());
    }

    @Test
    void reportTrack_whenDescriptionIsNull_savesNullDescription() {
        setAuthenticatedUser(7L);
        ReportRequest request = new ReportRequest("Spam", null);
        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);

        when(userService.getUserIfExistsById(7L)).thenReturn(
                User.builder().id(7L).username("listener").tier(AccountTier.FREE).build());
        when(trackService.getTrackIfExistsById(15L)).thenReturn(Track.builder().id(15L).build());
        when(reportSubmissionMapper.toTrackReportSubmittedResponse())
                .thenReturn(new MessageResponse("Track reported successfully"));

        reportService.reportTrack(15L, request);

        verify(reportRepository).save(reportCaptor.capture());
        assertNull(reportCaptor.getValue().getDescription());
    }

    @Test
    void reportTrack_whenReasonHasOuterWhitespace_trimsBeforeSaving() {
        setAuthenticatedUser(7L);
        ReportRequest request = new ReportRequest("  Spam  ", "Misleading metadata");
        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);

        when(userService.getUserIfExistsById(7L)).thenReturn(
                User.builder().id(7L).username("listener").tier(AccountTier.FREE).build());
        when(trackService.getTrackIfExistsById(15L)).thenReturn(Track.builder().id(15L).build());
        when(reportSubmissionMapper.toTrackReportSubmittedResponse())
                .thenReturn(new MessageResponse("Track reported successfully"));

        reportService.reportTrack(15L, request);

        verify(reportRepository).save(reportCaptor.capture());
        assertEquals("Spam", reportCaptor.getValue().getReason());
    }

    @Test
    void reportTrack_whenAuthenticationIsMissing_throwsUnauthorizedAndDoesNotPersist() {
        SecurityContextHolder.clearContext();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reportService.reportTrack(15L, new ReportRequest("Spam", null)));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Authentication token is missing", exception.getReason());
        verify(userService, never()).getUserIfExistsById(any());
        verify(trackService, never()).getTrackIfExistsById(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportTrack_whenTrackDoesNotExist_propagatesNotFoundAndDoesNotPersist() {
        setAuthenticatedUser(7L);
        when(userService.getUserIfExistsById(7L)).thenReturn(
                User.builder().id(7L).username("listener").tier(AccountTier.FREE).build());
        when(trackService.getTrackIfExistsById(15L))
                .thenThrow(new ResourceNotFoundException("Track with id 15 not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.reportTrack(15L, new ReportRequest("Spam", null)));

        assertEquals("Track with id 15 not found", exception.getMessage());
        verify(reportRepository, never()).save(any());
        verify(reportSubmissionMapper, never()).toTrackReportSubmittedResponse();
    }

    @Test
    void reportTrack_whenAuthenticatedUserDoesNotExist_propagatesNotFoundAndDoesNotLookupTrack() {
        setAuthenticatedUser(7L);
        when(userService.getUserIfExistsById(7L))
                .thenThrow(new ResourceNotFoundException("User with id 7 not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.reportTrack(15L, new ReportRequest("Spam", null)));

        assertEquals("User with id 7 not found", exception.getMessage());
        verify(trackService, never()).getTrackIfExistsById(any());
        verify(reportRepository, never()).save(any());
        verify(reportSubmissionMapper, never()).toTrackReportSubmittedResponse();
    }

    @Test
    void reportComment_whenRequestIsValid_savesCommentReportAndReturnsMessage() {
        setAuthenticatedUser(9L);
        ReportRequest request = new ReportRequest("Harassment", "Offensive reply");
        User user = User.builder().id(9L).username("listener").tier(AccountTier.FREE).build();
        Comment comment = Comment.builder().id(21L).build();
        MessageResponse mapperResponse = new MessageResponse("Comment reported successfully");
        ArgumentCaptor<Report> reportCaptor = ArgumentCaptor.forClass(Report.class);

        when(userService.getUserIfExistsById(9L)).thenReturn(user);
        when(commentService.getCommentIfExistsById(21L)).thenReturn(comment);
        when(reportSubmissionMapper.toCommentReportSubmittedResponse()).thenReturn(mapperResponse);

        MessageResponse response = reportService.reportComment(21L, request);

        assertEquals("Comment reported successfully", response.message());
        verify(reportRepository).save(reportCaptor.capture());
        Report savedReport = reportCaptor.getValue();
        assertEquals(9L, savedReport.getReporterId());
        assertEquals(21L, savedReport.getTargetId());
        assertEquals(ReportTargetType.COMMENT, savedReport.getTargetType());
        assertEquals("Harassment", savedReport.getReason());
        assertEquals("Offensive reply", savedReport.getDescription());
    }

    @Test
    void reportComment_whenAuthenticationIsMissing_throwsUnauthorizedAndDoesNotPersist() {
        SecurityContextHolder.clearContext();

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> reportService.reportComment(21L, new ReportRequest("Harassment", null)));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        assertEquals("Authentication token is missing", exception.getReason());
        verify(userService, never()).getUserIfExistsById(any());
        verify(commentService, never()).getCommentIfExistsById(any());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void reportComment_whenCommentDoesNotExist_propagatesNotFoundAndDoesNotPersist() {
        setAuthenticatedUser(9L);
        when(userService.getUserIfExistsById(9L)).thenReturn(
                User.builder().id(9L).username("listener").tier(AccountTier.FREE).build());
        when(commentService.getCommentIfExistsById(21L))
                .thenThrow(new ResourceNotFoundException("Comment with id 21 not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.reportComment(21L, new ReportRequest("Harassment", null)));

        assertEquals("Comment with id 21 not found", exception.getMessage());
        verify(reportRepository, never()).save(any());
        verify(reportSubmissionMapper, never()).toCommentReportSubmittedResponse();
    }

    @Test
    void reportComment_whenAuthenticatedUserDoesNotExist_propagatesNotFoundAndDoesNotLookupComment() {
        setAuthenticatedUser(9L);
        when(userService.getUserIfExistsById(9L))
                .thenThrow(new ResourceNotFoundException("User with id 9 not found"));

        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> reportService.reportComment(21L, new ReportRequest("Harassment", null)));

        assertEquals("User with id 9 not found", exception.getMessage());
        verify(commentService, never()).getCommentIfExistsById(any());
        verify(reportRepository, never()).save(any());
        verify(reportSubmissionMapper, never()).toCommentReportSubmittedResponse();
    }

    private void setAuthenticatedUser(Long userId) {
        UserPrincipal principal = UserPrincipal.builder()
                .id(userId)
                .username("listener")
                .tier(AccountTier.FREE)
                .build();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }
}
