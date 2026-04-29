package software.decibel.dtos.admin;

import java.time.LocalDateTime;

import lombok.Builder;
import software.decibel.enums.ReportStatus;
import software.decibel.enums.ReportTargetType;

@Builder
public record DetailedReportResponse(
        Long id,
        Long reporterId,
        ReportTargetType targetType,
        ReportStatus status,
        LocalDateTime createdAt,

        String reason,
        String description,
        Long targetId,
        Long targetUserId,
        String reporterUsername,
        String targetUsername,
        String targetDisplayName,

        // Shared between Track and Comment reports
        String targetTitle,
        LocalDateTime targetCreatedAt,

        // For track reports specifically
        String targetArtistName,
        String targetThumbnailUrl,
        Integer targetPlayCount,

        // For comment reports specifically
        String commentAuthor,
        String commentContent
) {}
