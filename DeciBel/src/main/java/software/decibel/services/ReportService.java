package software.decibel.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.moderation.ReportRequest;
import software.decibel.entities.Report;
import software.decibel.enums.ReportTargetType;
import software.decibel.mappers.ReportSubmissionMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserService;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserService userService;
    private final TrackService trackService;
    private final CommentService commentService;
    private final ReportSubmissionMapper reportSubmissionMapper;

    @Transactional
    public MessageResponse reportTrack(Long trackId, ReportRequest request) {
        Long reporterId = requireAuthenticatedUserId();
        userService.getUserIfExistsById(reporterId);
        trackService.getTrackIfExistsById(trackId);

        reportRepository.save(buildReport(reporterId, trackId, ReportTargetType.TRACK, request));
        return reportSubmissionMapper.toTrackReportSubmittedResponse();
    }

    @Transactional
    public MessageResponse reportComment(Long commentId, ReportRequest request) {
        Long reporterId = requireAuthenticatedUserId();
        userService.getUserIfExistsById(reporterId);
        commentService.getCommentIfExistsById(commentId);

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
}
