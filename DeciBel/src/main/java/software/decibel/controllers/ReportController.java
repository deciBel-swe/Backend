package software.decibel.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.moderation.ReportRequest;
import software.decibel.mappers.ReportSubmissionMapper;

@RestController
@RequiredArgsConstructor
public class ReportController {

    private final ReportSubmissionMapper reportSubmissionMapper;

    @PostMapping("/tracks/{trackId}/report")
    public ResponseEntity<MessageResponse> reportTrack(
            @PathVariable Long trackId,
            @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok(reportSubmissionMapper.toTrackReportSubmittedResponse());
    }

    @PostMapping("/comments/{commentId}/report")
    public ResponseEntity<MessageResponse> reportComment(
            @PathVariable Long commentId,
            @Valid @RequestBody ReportRequest request) {
        return ResponseEntity.ok(reportSubmissionMapper.toCommentReportSubmittedResponse());
    }
}
