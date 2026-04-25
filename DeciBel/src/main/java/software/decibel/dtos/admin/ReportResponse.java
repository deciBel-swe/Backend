package software.decibel.dtos.admin;

import java.time.LocalDateTime;

import lombok.Builder;
import software.decibel.enums.ReportStatus;
import software.decibel.enums.ReportTargetType;

/**
 * Response DTO for a single report. Matches the ReportResponse schema in the
 * OpenAPI spec. Note: targetId is intentionally excluded per the spec schema.
 */
@Builder
public record ReportResponse(
        Long id,
        Long reporterId,
        Long targetId,
        ReportTargetType targetType,
        String reason,
        String description,
        ReportStatus status,
        LocalDateTime createdAt
        ) {

}
