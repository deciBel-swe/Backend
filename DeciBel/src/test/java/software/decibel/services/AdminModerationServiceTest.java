package software.decibel.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import software.decibel.dtos.admin.ReportResponse;
import software.decibel.dtos.admin.UpdateReportStatusRequest;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.entities.Report;
import software.decibel.enums.ReportStatus;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.ReportMapper;
import software.decibel.repositories.ReportRepository;
import software.decibel.services.track.TrackService;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminModerationServiceTest {

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private ReportMapper reportMapper;

    @Mock
    private TrackService trackService;

    @InjectMocks
    private AdminModerationService adminModerationService;

    private Report report;

    @BeforeEach
    void setUp() {
        report = Report.builder()
                .id(1L)
                .status(ReportStatus.OPEN)
                .build();
    }

    @Test
    void getAllReports_returnsReportList() {
        Page<Report> page = new PageImpl<>(List.of(report));
        when(reportRepository.findAll(any(PageRequest.class))).thenReturn(page);
        
        ReportResponse response = new ReportResponse();
        response.setId(1L);
        when(reportMapper.toReportResponseList(any())).thenReturn(List.of(response));

        List<ReportResponse> result = adminModerationService.getAllReports(0, 10);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getId());
        verify(reportRepository).findAll(PageRequest.of(0, 10, Sort.by("createdAt").descending()));
    }

    @Test
    void updateReportStatus_whenReportExists_updatesAndReturnsSuccess() {
        UpdateReportStatusRequest request = new UpdateReportStatusRequest(ReportStatus.RESOLVED);
        when(reportRepository.findById(1L)).thenReturn(Optional.of(report));

        MessageResponse response = adminModerationService.updateReportStatus(1L, request);

        assertEquals("Report status updated successfully", response.message());
        assertEquals(ReportStatus.RESOLVED, report.getStatus());
        verify(reportRepository).save(report);
    }

    @Test
    void updateReportStatus_whenReportDoesNotExist_throwsException() {
        UpdateReportStatusRequest request = new UpdateReportStatusRequest(ReportStatus.RESOLVED);
        when(reportRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> 
            adminModerationService.updateReportStatus(1L, request));
        
        verify(reportRepository, never()).save(any());
    }

    @Test
    void adminDeleteTrack_callsTrackService() {
        adminModerationService.adminDeleteTrack(1L);
        verify(trackService).adminDeleteTrack(1L);
    }
}
