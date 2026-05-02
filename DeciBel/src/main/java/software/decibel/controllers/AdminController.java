package software.decibel.controllers;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import java.util.List;
import software.decibel.dtos.admin.AnalyticsResponse;
import software.decibel.dtos.admin.BanUserRequest;
import software.decibel.dtos.admin.BannedUsersPageResponse;
import software.decibel.dtos.admin.ListReportsRequest;
import software.decibel.dtos.admin.LoginAdminRequest;
import software.decibel.dtos.admin.LoginAdminResponse;
import software.decibel.dtos.admin.ReportResponse;
import software.decibel.dtos.admin.UpdateReportStatusRequest;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.admin.DetailedReportResponse;
import software.decibel.services.admin.AdminAuthService;
import software.decibel.services.admin.AdminModerationService;

@RestController
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminModerationService adminModerationService;

    @PostMapping("/login")
    public ResponseEntity<LoginAdminResponse> login(@Valid @RequestBody LoginAdminRequest request) {
        return ResponseEntity.ok(adminAuthService.login(request));
    }

    @GetMapping("/reports")
    public ResponseEntity<List<ReportResponse>> listReports(
            @RequestParam(required = false) Integer page,
            @RequestParam(required = false) Integer size) {
        ListReportsRequest request = ListReportsRequest.withDefaults(page, size);
        return ResponseEntity.ok(adminModerationService.getAllReports(request.page(), request.size()));
    }

    @GetMapping("/reports/{id}")
    public ResponseEntity<DetailedReportResponse> getReportById(@PathVariable Long id) {
        return ResponseEntity.ok(adminModerationService.getReportById(id));
    }

    @PatchMapping("/reports/{id}")
    public ResponseEntity<MessageResponse> updateReportStatus(
            @PathVariable Long id,
            @Valid @RequestBody UpdateReportStatusRequest request) {
        return ResponseEntity.ok(adminModerationService.updateReportStatus(id, request));
    }

    @PatchMapping("/users/{userId}/ban")
    public ResponseEntity<MessageResponse> banUser(
            @PathVariable Long userId,
            @Valid @RequestBody BanUserRequest request) {
        return ResponseEntity.ok(adminModerationService.banUser(userId, request));
    }

    @GetMapping("/users/banned")
    public ResponseEntity<BannedUsersPageResponse> getBannedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(adminModerationService.getBannedUsers(page, size));
    }

    @GetMapping("/analytics")
    public ResponseEntity<AnalyticsResponse> getPlatformAnalytics() {
        return ResponseEntity.ok(adminModerationService.getPlatformAnalytics());
    }

    @DeleteMapping("/tracks/{trackId}")
    public ResponseEntity<Void> deleteTrack(@PathVariable Long trackId) {
        adminModerationService.adminDeleteTrack(trackId);
        return ResponseEntity.noContent().build();
    }

}
