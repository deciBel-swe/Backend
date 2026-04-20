package software.decibel.dtos.admin;

import jakarta.validation.constraints.NotNull;
import software.decibel.enums.ReportStatus;

/**
 * Request body for PATCH /admin/reports/{id}.
 * Matches the UpdateReportRequest schema in the OpenAPI spec.
 */
public record UpdateReportStatusRequest(
    @NotNull(message = "Status is required")
    ReportStatus status
) {}
