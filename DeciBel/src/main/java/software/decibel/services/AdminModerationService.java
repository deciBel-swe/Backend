package software.decibel.services;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.admin.AnalyticsResponse;
import software.decibel.dtos.admin.BanUserRequest;
import software.decibel.dtos.admin.BannedUsersPageResponse;
import software.decibel.dtos.admin.BannedUserResponse;
import software.decibel.dtos.admin.ReportResponse;
import software.decibel.dtos.admin.UpdateReportStatusRequest;
import software.decibel.dtos.auth.AdminPrincipal;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.entities.Report;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.AdminUserMapper;
import software.decibel.mappers.ReportMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.track.TrackService;

@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    private final TrackService trackService;
    private final UserRepository userRepository;
    private final AdminUserMapper adminUserMapper;
    private final TrackRepository trackRepository;

    public List<ReportResponse> getAllReports(int page, int size) {
        List<Report> reports = reportRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending())).getContent();
        return reportMapper.toReportResponseList(reports);
    }

    @Transactional(readOnly = true)
    public ReportResponse getReportById(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report with id " + reportId + " not found"));
        return reportMapper.toReportResponse(report);
    }

    @Transactional
    public MessageResponse updateReportStatus(Long reportId, UpdateReportStatusRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report with id " + reportId + " not found"));

        if (report.getStatus() == request.status()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Report is already in the requested status");
        }

        report.setStatus(request.status());
        reportRepository.save(report);
        return new MessageResponse("Report status updated successfully");
    }

    @Transactional
    public void adminDeleteTrack(Long trackId) {
        trackService.adminDeleteTrack(trackId);
    }

    @Transactional
    public MessageResponse banUser(Long userId, BanUserRequest request) {
        requireAdmin();

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        user.setBanned(request.isBanned());
        userRepository.save(user);

        return new MessageResponse(request.isBanned()
                ? "User banned successfully"
                : "User unbanned successfully");
    }

    @Transactional(readOnly = true)
    public BannedUsersPageResponse getBannedUsers(int page, int size) {
        requireAdmin();

        Page<User> bannedUsersPage = userRepository.findByIsBannedTrue(
                PageRequest.of(page, size, Sort.by("updatedAt").descending().and(Sort.by("id").descending())));

        List<BannedUserResponse> content = bannedUsersPage.getContent().stream()
                .map(adminUserMapper::toBannedUserResponse)
                .toList();

        return adminUserMapper.toBannedUsersPageResponse(
                content,
                bannedUsersPage.getNumber(),
                bannedUsersPage.getSize(),
                bannedUsersPage.getTotalElements(),
                bannedUsersPage.getTotalPages(),
                bannedUsersPage.isLast(),
                userRepository.countByIsBannedTrue());
    }

    @Transactional(readOnly = true)
    public AnalyticsResponse getPlatformAnalytics() {
        requireAdmin();

        Long totalUsers = userRepository.count();
        Long totalTracks = trackRepository.count();
        Long totalPlays = trackRepository.sumPlayCount();
        Double playThroughRate = trackRepository.averagePlayThroughRate();

        return new AnalyticsResponse(
                totalUsers,
                totalTracks,
                totalPlays != null ? totalPlays : 0L,
                playThroughRate != null ? playThroughRate : 0.0,
                0L);
    }

    private void requireAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null
                || !authentication.isAuthenticated()
                || authentication instanceof AnonymousAuthenticationToken) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication token is missing");
        }

        if (!(authentication.getPrincipal() instanceof AdminPrincipal)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin privileges are required");
        }
    }
}
