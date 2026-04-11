package software.decibel.dtos.admin;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import software.decibel.enums.ReportStatus;
import software.decibel.enums.ReportTargetType;

/**
 * Response DTO for a single report.
 * Matches the ReportResponse schema in the OpenAPI spec.
 * Note: targetId is intentionally excluded per the spec schema.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReportResponse {
    private Long id;
    private Long reporterId;
    private ReportTargetType targetType;
    private ReportStatus status;
    private LocalDateTime createdAt;
}
