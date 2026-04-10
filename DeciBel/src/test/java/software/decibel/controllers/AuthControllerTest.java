package software.decibel.controllers;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import software.decibel.dtos.auth.AuthLoginResult;
import software.decibel.dtos.auth.AuthTokenRotationResult;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.GoogleOauthRequest;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.LoginLocalResponse;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RefreshTokenResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.enums.AccountTier;
import software.decibel.enums.DeviceType;
import software.decibel.services.AuthService;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock
    private AuthService authService;

    private MockMvc mockMvc;
    private MockMvc productionMockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        AuthController controller = new AuthController(authService, "local");
        AuthController productionController = new AuthController(authService, "prod");
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .build();
        productionMockMvc = MockMvcBuilders.standaloneSetup(productionController)
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
                new LoginLocalResponse.UserInfo(2L, "pro-user", AccountTier.PRO, null,
                        "avatar.png", false));
        when(authService.loginLocal(any(LoginLocalRequest.class)))
                .thenReturn(new AuthLoginResult(response, "refresh-token", 2592000L));

        mockMvc.perform(post("/auth/login/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.expiresIn").value(1800))
                .andExpect(jsonPath("$.user.id").value(2))
                .andExpect(jsonPath("$.user.username").value("pro-user"))
                .andExpect(jsonPath("$.user.tier").value("PRO"))
                .andExpect(jsonPath("$.user.isNewUser").value(false));
    }

    @Test
    void loginLocal_whenRunningInProduction_setsSecureRefreshCookie() throws Exception {
        LoginLocalResponse response = new LoginLocalResponse(
                "access-token",
                1800L,
                new LoginLocalResponse.UserInfo(2L, "pro-user", AccountTier.PRO, null,
                        "avatar.png", false));
        when(authService.loginLocal(any(LoginLocalRequest.class)))
                .thenReturn(new AuthLoginResult(response, "refresh-token", 2592000L));

        productionMockMvc.perform(post("/auth/login/local")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest())))
                .andExpect(status().isOk())
                .andExpect(cookie().secure("refreshToken", true));
    }

    @Test
    void verifyEmail_whenRequestIsValid_returnsMessageWithoutRefreshCookie() throws Exception {
        when(authService.verifyEmail(any(VerifyEmailRequest.class)))
                .thenReturn(new MessageResponse("Email verified"));

        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new VerifyEmailRequest("verify-token"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified"));
    }

    @Test
    void logout_whenRequestIsValid_clearsRefreshCookieAndReturnsMessage() throws Exception {
        when(authService.logout(anyString()))
                .thenReturn(new MessageResponse("Logged out successfully"));

        // Changed to "refreshToken" (camelCase)
        mockMvc.perform(post("/auth/logout")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", "dummy-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", ""))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.message").value("Logged out successfully"));

        verify(authService).logout("dummy-token");
    }

    @Test
    void logoutAll_whenRequestIsValid_clearsRefreshCookieAndReturnsMessage() throws Exception {
        when(authService.logoutAll(anyString()))
                .thenReturn(new MessageResponse("Logged out of all sessions"));

        // Changed to "refreshToken" (camelCase)
        mockMvc.perform(post("/auth/logout-all")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", "dummy-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", ""))
                .andExpect(cookie().maxAge("refreshToken", 0))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.message").value("Logged out from all sessions successfully"));

        verify(authService).logoutAll("dummy-token");
    }

    @Test
    void verifyEmail_whenBodyIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/verify-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(authService);
    }

    @Test
    void refreshToken_whenCookieIsPresent_returnsNewAccessTokenAndNewCookie() throws Exception {
        RefreshTokenResponse body = new RefreshTokenResponse("new-access-token", 1800L);
        AuthTokenRotationResult result = new AuthTokenRotationResult(body,
                "new-refresh-token", 2592000L);
        when(authService.refreshToken("valid-refresh-token")).thenReturn(result);

        mockMvc.perform(post("/auth/refreshtoken")
                .cookie(new jakarta.servlet.http.Cookie("refreshToken", "valid-refresh-token")))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "new-refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.accessToken").value("new-access-token"))
                .andExpect(jsonPath("$.expiresIn").value(1800));
    }

    @Test
    void refreshToken_whenCookieIsMissing_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/refreshtoken"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void exchangeGoogleOauthToken_whenRequestIsValid_returnsBodyAndRefreshCookie() throws Exception {
        LoginLocalResponse response = new LoginLocalResponse(
                "google-access-token",
                1800L,
                new LoginLocalResponse.UserInfo(3L, "google-user", AccountTier.FREE,
                        "/users/google-user", "avatar.png", true));
        when(authService.loginWithGoogle(any(GoogleOauthRequest.class)))
                .thenReturn(new AuthLoginResult(response, "google-refresh-token",
                        2592000L));

        mockMvc.perform(post("/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(googleOauthRequest())))
                .andExpect(status().isOk())
                .andExpect(cookie().value("refreshToken", "google-refresh-token"))
                .andExpect(cookie().httpOnly("refreshToken", true))
                .andExpect(jsonPath("$.accessToken").value("google-access-token"))
                .andExpect(jsonPath("$.user.id").value(3))
                .andExpect(jsonPath("$.user.username").value("google-user"))
                .andExpect(jsonPath("$.user.isNewUser").value(true));
    }

    @Test
    void exchangeGoogleOauthToken_whenBodyIsInvalid_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/auth/oauth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"authTokenDto\":\"\",\"deviceInfo\":null}"))
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

    private GoogleOauthRequest googleOauthRequest() {
        return new GoogleOauthRequest(
                "google-id-token",
                new DeviceInfo(DeviceType.DESKTOP, "fingerprint-3", "Chrome on Windows"));
    }
}
