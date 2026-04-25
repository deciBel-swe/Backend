package software.decibel.mappers;

import java.util.List;

import org.mapstruct.Mapper;
import software.decibel.dtos.admin.ReportResponse;
import software.decibel.entities.Report;

/**
 * Maps the Report entity to its API response DTOs. targetId is intentionally
 * excluded from ReportResponse per the OpenAPI specification. GET
 * /admin/reports returns a plain array (List<ReportResponse>) — "type: array"
 * in the spec.
 */
@Mapper(componentModel = "spring")
public interface ReportMapper {

    ReportResponse toReportResponse(Report report);

    List<ReportResponse> toReportResponseList(List<Report> reports);
}
