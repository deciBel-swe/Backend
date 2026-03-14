package software.decibel.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.LoginLocalResponse;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.enums.AccountTier;
import software.decibel.enums.DeviceType;
import software.decibel.services.AuthService;

import java.time.LocalDate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, "local");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();

        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());
    }

    @Test
    void registerLocal_whenRequestIsValid_returnsCreated() throws Exception {
        when(authService.registerLocal(any(RegisterLocalRequest.class)))
                .thenReturn(new MessageResponse("User Generated successfully"));

        mockMvc.perform(post("/auth/register/local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(registerRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User Generated successfully"));
    }

    @Test
    void registerLocal_whenBodyIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/register/local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"\",\"username\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void loginLocal_whenRequestIsValid_returnsBodyAndRefreshCookie() throws Exception {
        LoginLocalResponse response = new LoginLocalResponse(
                "access-token",
                1800L,
                new LoginLocalResponse.UserInfo(2L, "artist-user", AccountTier.ARTIST, null, "avatar.png"));
        when(authService.loginLocal(any(LoginLocalRequest.class)))
                .thenReturn(new AuthService.AuthLoginResult(response, "refresh-token", 2592000L));

        mockMvc.perform(post("/auth/login/local")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(2))
                .andExpect(jsonPath("$.user.username").value("artist-user"))
                .andExpect(jsonPath("$.user.tier").value("ARTIST"));
    }

    @Test
    void verifyEmail_whenRequestIsValid_returnsMessageAndRefreshCookie() throws Exception {
        when(authService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenReturn(new AuthService.AuthRefreshTokenResult("refresh-token", 2592000L));

        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new VerifyEmailRequest("verify-token"))))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.message").value("Email verified"));
    }

    @Test
    void verifyEmail_whenBodyIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/verify-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    private RegisterLocalRequest registerRequest() {
        return new RegisterLocalRequest(
                "new@example.com",
                "new-user",
                "Password123",
                LocalDate.of(2000, 1, 1),
                "MALE",
                "Cairo",
                "Egypt",
                "captcha-token",
                new DeviceInfo(DeviceType.DESKTOP, "fingerprint-1", "Dell"));
    }

    private LoginLocalRequest loginRequest() {
        return new LoginLocalRequest(
                "verified@example.com",
                "Password123",
                new DeviceInfo(DeviceType.MOBILE, "fingerprint-2", "Phone"));
    }
}
