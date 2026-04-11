package software.decibel.services;

import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.admin.ReportResponse;
import software.decibel.dtos.admin.UpdateReportStatusRequest;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.entities.Report;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.ReportMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.services.track.TrackService;

@Service
@RequiredArgsConstructor
public class AdminModerationService {

    private final ReportRepository reportRepository;
    private final ReportMapper reportMapper;
    private final TrackService trackService;

    public List<ReportResponse> getAllReports(int page, int size) {
        List<Report> reports = reportRepository.findAll(
                PageRequest.of(page, size, Sort.by("createdAt").descending())).getContent();
        return reportMapper.toReportResponseList(reports);
    }

    @Transactional
    public MessageResponse updateReportStatus(Long reportId, UpdateReportStatusRequest request) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Report with id " + reportId + " not found"));

        report.setStatus(request.status());
        reportRepository.save(report);
        return new MessageResponse("Report status updated successfully");
    }

    @Transactional
    public void adminDeleteTrack(Long trackId) {
        trackService.adminDeleteTrack(trackId);
    }
}
