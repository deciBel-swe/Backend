package software.decibel.services.admin;

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
import software.decibel.dtos.admin.BannedUserResponse;
import software.decibel.dtos.admin.BannedUsersPageResponse;
import software.decibel.dtos.admin.ReportResponse;
import software.decibel.dtos.admin.UpdateReportStatusRequest;
import software.decibel.dtos.auth.AdminPrincipal;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.admin.DetailedReportResponse;
import software.decibel.dtos.admin.DetailedReportResponse.DetailedReportResponseBuilder;
import software.decibel.entities.Report;
import software.decibel.entities.User;
import software.decibel.entities.Track;
import software.decibel.entities.Comment;
import software.decibel.enums.ReportTargetType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.AdminUserMapper;
import software.decibel.mappers.ReportMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.repositories.CommentRepository;
import software.decibel.services.track.TrackService;
import software.decibel.utils.FileUtilityAzure;

@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    private final TrackService trackService;
    private final UserRepository userRepository;
    private final AdminUserMapper adminUserMapper;
    private final TrackRepository trackRepository;
    private final FileUtilityAzure fileUtilityAzure;
    private final CommentRepository commentRepository;

    public List<ReportResponse> getAllReports(int page, int size) {
        List<Report> reports = reportRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending())).getContent();
        return reportMapper.toReportResponseList(reports);
    }

    @Transactional(readOnly = true)
    public DetailedReportResponse getReportById(Long reportId) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report with id " + reportId + " not found"));

        User reporter = userRepository.findById(report.getReporterId())
                .orElseThrow(() -> new ResourceNotFoundException("Reporter not found"));

        DetailedReportResponseBuilder builder = DetailedReportResponse.builder()
                .id(report.getId())
                .reporterId(report.getReporterId())
                .targetType(report.getTargetType())
                .status(report.getStatus())
                .createdAt(report.getCreatedAt())
                .reason(report.getReason())
                .description(report.getDescription())
                .targetId(report.getTargetId())
                .reporterUsername(reporter.getUsername());

        if (report.getTargetType() == ReportTargetType.TRACK) {
            Track track = trackRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Track not found"));
            User targetUser = track.getUploader();
            builder.targetUserId(targetUser.getId())
                   .targetUsername(targetUser.getUsername())
                   .targetDisplayName(targetUser.getDisplayName())
                   .targetTitle(track.getTitle())
                   .targetArtistName(targetUser.getDisplayName() != null && !targetUser.getDisplayName().isEmpty() ? targetUser.getDisplayName() : targetUser.getUsername())
                   .targetThumbnailUrl(track.getCoverUrl())
                   .targetPlayCount(track.getPlayCount())
                   .targetCreatedAt(track.getUploadDate());
        } else if (report.getTargetType() == software.decibel.enums.ReportTargetType.COMMENT) {
            Comment comment = commentRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comment not found"));
            User targetUser = comment.getUser();
            Track track = comment.getTrack();
            builder.targetUserId(targetUser.getId())
                   .targetUsername(targetUser.getUsername())
                   .targetDisplayName(targetUser.getDisplayName())
                   .commentAuthor(targetUser.getUsername())
                   .commentContent(comment.getContent())
                   .targetTitle(track.getTitle())
                   .targetCreatedAt(comment.getCreatedAt());
        } else if (report.getTargetType() == software.decibel.enums.ReportTargetType.USER) {
            User targetUser = userRepository.findById(report.getTargetId())
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            builder.targetUserId(targetUser.getId())
                   .targetUsername(targetUser.getUsername())
                   .targetDisplayName(targetUser.getDisplayName());
        }

        return builder.build();
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
        Long totalStorageBytes = fileUtilityAzure.getTotalStorageUsed();
        Long totalStorageCapacityBytes = fileUtilityAzure.getTotalStorageCapacity();

        return new AnalyticsResponse(
                totalUsers,
                totalTracks,
                totalPlays != null ? totalPlays : 0L,
                playThroughRate != null ? playThroughRate : 0.0,
                totalStorageBytes,
                totalStorageCapacityBytes);
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
