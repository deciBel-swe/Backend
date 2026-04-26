package software.decibel.services;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.admin.AnalyticsResponse;
import software.decibel.dtos.admin.BanUserRequest;
import software.decibel.dtos.admin.BannedUserResponse;
import software.decibel.dtos.admin.ReportResponse;
import software.decibel.dtos.admin.UpdateReportStatusRequest;
import software.decibel.dtos.auth.AdminPrincipal;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.entities.Report;
import software.decibel.entities.User;
import software.decibel.enums.ReportStatus;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.AdminUserMapper;
import software.decibel.mappers.ReportMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.admin.AdminModerationService;
import software.decibel.services.track.TrackService;
import software.decibel.utils.FileUtilityAzure;

@ExtendWith(MockitoExtension.class)
class AdminModerationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private FileUtilityAzure fileUtilityAzure;

    @Mock
    private TrackService trackService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AdminUserMapper adminUserMapper;

    @Mock
    private TrackRepository trackRepository;

    @InjectMocks
    private AdminModerationService adminModerationService;

    private Report report;
    private User user;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        report = Report.builder()
                .id(1L)
                .status(ReportStatus.OPEN)
                .build();
        user = User.builder()
                .id(7L)
                .username("target-user")
                .displayName("Target User")
                .avatarUrl("avatar.png")
                .build();
    }

    @AfterEach
    void tearDown() {
        // Clear SecurityContext to prevent AdminPrincipal from polluting other tests
        SecurityContextHolder.clearContext();
    }

    @Test
    void getAllReports_returnsReportList() {
        Page<Report> page = new PageImpl<>(List.of(report));
        when(reportRepository.findAll(any(PageRequest.class))).thenReturn(page);

        ReportResponse response = ReportResponse.builder()
                .id(1L)
                .build();
        when(reportMapper.toReportResponseList(any())).thenReturn(List.of(response));

        List<ReportResponse> result = adminModerationService.getAllReports(0, 10);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).id());
        verify(reportRepository).findAll(PageRequest.of(0, 10, Sort.by("createdAt").descending()));
    }

    @Test
    void getReportById_whenReportExists_returnsMappedResponse() {
        ReportResponse response = ReportResponse.builder().id(1L).build();
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));
        when(reportMapper.toReportResponse(report)).thenReturn(response);

        ReportResponse result = adminModerationService.getReportById(1L);

        assertEquals(1L, result.id());
    }

    @Test
    void updateReportStatus_whenReportExists_updatesAndReturnsSuccess() {
        UpdateReportStatusRequest request = new UpdateReportStatusRequest(ReportStatus.RESOLVED);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        MessageResponse response = adminModerationService.updateReportStatus(1L, request);

        assertEquals("Report status updated successfully", response.message());
        assertEquals(ReportStatus.RESOLVED, report.getStatus());
        verify(reportRepository).save(report);
    }

    @Test
    void updateReportStatus_whenRequestedStatusMatchesCurrent_throwsConflict() {
        UpdateReportStatusRequest request = new UpdateReportStatusRequest(ReportStatus.OPEN);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> adminModerationService.updateReportStatus(1L, request));

        assertEquals(409, exception.getStatusCode().value());
        verify(reportRepository, never()).save(any());
    }

    @Test
    void updateReportStatus_whenReportDoesNotExist_throwsException() {
        UpdateReportStatusRequest request = new UpdateReportStatusRequest(ReportStatus.RESOLVED);
        when(reportRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, ()
                -> adminModerationService.updateReportStatus(1L, request));

        verify(reportRepository, never()).save(any());
    }

    @Test
    void adminDeleteTrack_callsTrackService() {
        adminModerationService.adminDeleteTrack(1L);
        verify(trackService).adminDeleteTrack(1L);
    }

    @Test
    void banUser_whenAdminAndTargetExists_updatesBanState() {
        mockAdminAuth();
        when(userRepository.findById(7L)).thenReturn(Optional.of(user));

        MessageResponse response = adminModerationService.banUser(7L, new BanUserRequest(true));

        assertEquals("User banned successfully", response.message());
        assertTrue(user.isBanned());
        verify(userRepository).save(user);
    }

    @Test
    void banUser_whenNoAuthentication_throwsUnauthorized() {
        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> adminModerationService.banUser(7L, new BanUserRequest(true)));

        assertEquals(401, exception.getStatusCode().value());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void banUser_whenNonAdminPrincipal_throwsForbidden() {
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("user", null, Collections.emptyList()));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> adminModerationService.banUser(7L, new BanUserRequest(true)));

        assertEquals(403, exception.getStatusCode().value());
        verify(userRepository, never()).findById(any());
    }

    @Test
    void banUser_whenTargetMissing_throwsNotFound() {
        mockAdminAuth();
        when(userRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class,
                () -> adminModerationService.banUser(7L, new BanUserRequest(true)));

        verify(userRepository, never()).save(any());
    }

    @Test
    void getBannedUsers_whenAdmin_returnsMappedPageWithTotalCount() {
        mockAdminAuth();
        Page<User> page = new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1);
        BannedUserResponse userResponse = new BannedUserResponse(7L, "target-user", "Target User", "avatar.png", true);

        when(userRepository.findByIsBannedTrue(PageRequest.of(0, 20, Sort.by("updatedAt").descending().and(Sort.by("id").descending()))))
                .thenReturn(page);
        when(adminUserMapper.toBannedUserResponse(user)).thenReturn(userResponse);
        when(userRepository.countByIsBannedTrue()).thenReturn(5L);
        when(adminUserMapper.toBannedUsersPageResponse(List.of(userResponse), 0, 20, 1L, 1, true, 5L))
                .thenCallRealMethod();

        var result = adminModerationService.getBannedUsers(0, 20);

        assertEquals(1, result.content().size());
        assertEquals(5L, result.totalBannedUsers());
        assertEquals("target-user", result.content().get(0).username());
    }

    @Test
    void getPlatformAnalytics_whenAdmin_returnsComputedMetrics() {
        mockAdminAuth();
        when(userRepository.count()).thenReturn(10L);
        when(trackRepository.count()).thenReturn(4L);
        when(trackRepository.sumPlayCount()).thenReturn(120L);
        when(trackRepository.averagePlayThroughRate()).thenReturn(73.5);

        AnalyticsResponse result = adminModerationService.getPlatformAnalytics();

        assertEquals(10L, result.totalUsers());
        assertEquals(4L, result.totalTracks());
        assertEquals(120L, result.totalPlays());
        assertEquals(73.5, result.playThroughRate());
        assertEquals(0L, result.totalStorageUsedBytes());
    }

    private void mockAdminAuth() {
        AdminPrincipal adminPrincipal = AdminPrincipal.builder()
                .id(1L)
                .username("admin")
                .build();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(adminPrincipal, null, adminPrincipal.getAuthorities()));
    }
}
