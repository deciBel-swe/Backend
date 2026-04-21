package software.decibel.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import software.decibel.dtos.admin.AdminUserResponse;
import software.decibel.dtos.admin.AnalyticsResponse;
import software.decibel.dtos.admin.BanUserRequest;
import software.decibel.dtos.admin.BannedUserResponse;
import software.decibel.dtos.admin.BannedUsersPageResponse;
import software.decibel.dtos.admin.LoginAdminRequest;
import software.decibel.dtos.admin.LoginAdminResponse;
import software.decibel.dtos.admin.ReportResponse;
import software.decibel.dtos.admin.UpdateReportStatusRequest;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.enums.DeviceType;
import software.decibel.enums.ReportStatus;
import software.decibel.exceptions.AdminExceptionHandler;
import software.decibel.exceptions.custom.InvalidAdminCredentialsException;
import software.decibel.services.AdminAuthService;
import software.decibel.services.AdminModerationService;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminAuthService adminAuthService;

    @Mock
    private AdminModerationService adminModerationService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(adminAuthService, adminModerationService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new AdminExceptionHandler())
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
    }

    @Test
    void login_whenValidRequest_returnsOkAndJson() throws Exception {
        LoginAdminResponse response = LoginAdminResponse.builder()
                .accessToken("test-admin-token")
                .expiresIn(3600L)
                .adminUser(AdminUserResponse.builder().id(1L).username("admin").avatarUrl("img.png").build())
                .build();

        when(adminAuthService.login(any(LoginAdminRequest.class))).thenReturn(response);

        LoginAdminRequest request = new LoginAdminRequest("admin@test.com", "password", new DeviceInfo(DeviceType.DESKTOP, "fp", "name"));

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("test-admin-token"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.adminUser.username").value("admin"));
    }

    @Test
    void login_whenMissingDeviceInfo_returnsBadRequestText() throws Exception {
        LoginAdminRequest request = new LoginAdminRequest("admin@test.com", "password", null);

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("One or more fields are invalid."));
        
        verifyNoInteractions(adminAuthService);
    }

    @Test
    void login_whenMissingEmailOrPassword_returnsBadRequestText() throws Exception {
        LoginAdminRequest request = new LoginAdminRequest("  ", "", new DeviceInfo(DeviceType.DESKTOP, "fp", "name"));

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("One or more fields are invalid."));
        
        verifyNoInteractions(adminAuthService);
    }

    @Test
    void login_whenBodyCompletelyMissing_returnsBadRequestText() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Malformed JSON request body."));

        verifyNoInteractions(adminAuthService);
    }

    @Test
    void login_whenCredentialsInvalid_returnsUnauthorizedEmptyMap() throws Exception {
        when(adminAuthService.login(any(LoginAdminRequest.class)))
                .thenThrow(new InvalidAdminCredentialsException("Invalid"));

        LoginAdminRequest request = new LoginAdminRequest("admin@test.com", "wrong", new DeviceInfo(DeviceType.DESKTOP, "fp", "name"));

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.message").value("Invalid email or password."));
    }

    @Test
    void login_whenWrongHttpMethod_returnsMethodNotAllowed() throws Exception {
        mockMvc.perform(get("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed());
    }

    @Test
    void listReports_returnsOkAndJson() throws Exception {
        ReportResponse report = ReportResponse.builder()
                .id(1L)
                .reporterId(100L)
                .status(ReportStatus.OPEN)
                .build();
        when(adminModerationService.getAllReports(anyInt(), anyInt())).thenReturn(List.of(report));

        mockMvc.perform(get("/admin/reports")
                        .param("page", "0")
                        .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].status").value("OPEN"));
    }

    @Test
    void getReportById_returnsOkAndJson() throws Exception {
        ReportResponse report = ReportResponse.builder()
                .id(1L)
                .reporterId(100L)
                .status(ReportStatus.OPEN)
                .build();
        when(adminModerationService.getReportById(1L)).thenReturn(report);

        mockMvc.perform(get("/admin/reports/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("OPEN"));
    }

    @Test
    void updateReportStatus_whenValidRequest_returnsOk() throws Exception {
        UpdateReportStatusRequest request = new UpdateReportStatusRequest(ReportStatus.RESOLVED);
        when(adminModerationService.updateReportStatus(eq(1L), any())).thenReturn(new MessageResponse("Success"));

        mockMvc.perform(patch("/admin/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Success"));
    }

    @Test
    void updateReportStatus_whenInvalidRequest_returnsBadRequest() throws Exception {
        // status is @NotNull, so null should fail
        UpdateReportStatusRequest request = new UpdateReportStatusRequest(null);

        mockMvc.perform(patch("/admin/reports/1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"))
                .andExpect(jsonPath("$.message").value("One or more fields are invalid."));
    }

    @Test
    void banUser_whenValidRequest_returnsOk() throws Exception {
        BanUserRequest request = new BanUserRequest(true);
        when(adminModerationService.banUser(eq(5L), any(BanUserRequest.class)))
                .thenReturn(new MessageResponse("User banned successfully"));

        mockMvc.perform(patch("/admin/users/5/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("User banned successfully"));
    }

    @Test
    void banUser_whenIsBannedMissing_returnsBadRequest() throws Exception {
        String request = "{}";

        mockMvc.perform(patch("/admin/users/5/ban")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Validation Failed"));

        verifyNoInteractions(adminModerationService);
    }

    @Test
    void getBannedUsers_returnsOkAndJson() throws Exception {
        BannedUsersPageResponse response = new BannedUsersPageResponse(
                List.of(new BannedUserResponse(5L, "banned-user", "Banned User", "avatar.png", true)),
                0,
                20,
                1,
                1,
                true,
                3);
        when(adminModerationService.getBannedUsers(0, 20)).thenReturn(response);

        mockMvc.perform(get("/admin/users/banned")
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].username").value("banned-user"))
                .andExpect(jsonPath("$.totalBannedUsers").value(3));
    }

    @Test
    void getPlatformAnalytics_returnsOkAndJson() throws Exception {
        when(adminModerationService.getPlatformAnalytics())
                .thenReturn(new AnalyticsResponse(10L, 4L, 120L, 73.5, 0L));

        mockMvc.perform(get("/admin/analytics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers").value(10))
                .andExpect(jsonPath("$.totalTracks").value(4))
                .andExpect(jsonPath("$.totalPlays").value(120));
    }
}
