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
import software.decibel.dtos.admin.LoginAdminRequest;
import software.decibel.dtos.admin.LoginAdminResponse;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.enums.DeviceType;
import software.decibel.exceptions.AdminExceptionHandler;
import software.decibel.exceptions.custom.InvalidAdminCredentialsException;
import software.decibel.services.AdminAuthService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AdminControllerTest {

    @Mock
    private AdminAuthService adminAuthService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AdminController controller = new AdminController(adminAuthService);
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

        LoginAdminRequest request = LoginAdminRequest.builder()
                .email("admin@test.com")
                .password("password")
                .deviceInfo(new DeviceInfo(DeviceType.DESKTOP, "fp", "name"))
                .build();

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
        LoginAdminRequest request = LoginAdminRequest.builder()
                .email("admin@test.com")
                .password("password")
                .build();

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Bad Request"));
        
        verifyNoInteractions(adminAuthService);
    }

    @Test
    void login_whenMissingEmailOrPassword_returnsBadRequestText() throws Exception {
        LoginAdminRequest request = LoginAdminRequest.builder()
                .email("  ") // Blank email -> @NotBlank constraint failure
                .password("") // Empty password -> @NotBlank constraint failure
                .deviceInfo(new DeviceInfo(DeviceType.DESKTOP, "fp", "name"))
                .build();

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Bad Request"));
        
        verifyNoInteractions(adminAuthService);
    }

    @Test
    void login_whenBodyCompletelyMissing_returnsBadRequestText() throws Exception {
        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Bad Request"));

        verifyNoInteractions(adminAuthService);
    }

    @Test
    void login_whenCredentialsInvalid_returnsUnauthorizedEmptyMap() throws Exception {
        when(adminAuthService.login(any(LoginAdminRequest.class)))
                .thenThrow(new InvalidAdminCredentialsException("Invalid"));

        LoginAdminRequest request = LoginAdminRequest.builder()
                .email("admin@test.com")
                .password("wrong")
                .deviceInfo(new DeviceInfo(DeviceType.DESKTOP, "fp", "name"))
                .build();

        mockMvc.perform(post("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{}"));
    }

    @Test
    void login_whenWrongHttpMethod_returnsMethodNotAllowed() throws Exception {
        // Checking security against brute-forcing headers or REST calls
        mockMvc.perform(get("/admin/login")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isMethodNotAllowed());
    }
}
