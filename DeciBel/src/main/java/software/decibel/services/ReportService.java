package software.decibel.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.moderation.ReportRequest;
import software.decibel.entities.Report;
import software.decibel.enums.ReportStatus;
import software.decibel.enums.ReportTargetType;
import software.decibel.mappers.ReportSubmissionMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.services.user.UserService;
import software.decibel.utils.TrackChecksUtil;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserService userService;
    private final TrackChecksUtil trackChecksUtil;
    private final CommentService commentService;
    private final ReportSubmissionMapper reportSubmissionMapper;

    @Transactional
    public MessageResponse reportTrack(Long trackId, ReportRequest request) {
        Long reporterId = requireAuthenticatedUserId();
        userService.getUserIfExistsById(reporterId);
        trackChecksUtil.getTrackIfExistsById(trackId);
        ensureNoOpenReportExists(reporterId, trackId, ReportTargetType.TRACK);

        reportRepository.save(buildReport(reporterId, trackId, ReportTargetType.TRACK, request));
        return reportSubmissionMapper.toTrackReportSubmittedResponse();
    }

    @Transactional
    public MessageResponse reportComment(Long commentId, ReportRequest request) {
        Long reporterId = requireAuthenticatedUserId();
        userService.getUserIfExistsById(reporterId);
        commentService.getCommentIfExistsById(commentId);
        ensureNoOpenReportExists(reporterId, commentId, ReportTargetType.COMMENT);

        reportRepository.save(buildReport(reporterId, commentId, ReportTargetType.COMMENT, request));
        return reportSubmissionMapper.toCommentReportSubmittedResponse();
    }

    private Long requireAuthenticatedUserId() {
        Long userId = JwtService.getCurrentUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication token is missing");
        }
        return userId;
    }

    private Report buildReport(Long reporterId, Long targetId, ReportTargetType targetType, ReportRequest request) {
        return Report.builder()
                .reporterId(reporterId)
                .targetId(targetId)
                .targetType(targetType)
                .reason(request.reason().trim())
                .description(request.description())
                .build();
    }

    private void ensureNoOpenReportExists(Long reporterId, Long targetId, ReportTargetType targetType) {
        if (reportRepository.existsByReporterIdAndTargetIdAndTargetTypeAndStatus(
                reporterId, targetId, targetType, ReportStatus.OPEN)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "An open report already exists for this target");
        }
    }
}
