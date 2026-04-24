package software.decibel.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import software.decibel.dtos.auth.google.GoogleClientConfig;
import software.decibel.dtos.auth.google.GoogleTokenInfoResponse;
import software.decibel.enums.DeviceType;
import software.decibel.exceptions.custom.InvalidGoogleTokenException;
import software.decibel.services.auth.GoogleTokenVerificationService;

class GoogleTokenVerificationServiceTest {

    private final GoogleTokenVerificationService service = new GoogleTokenVerificationService(
            RestClient.builder(),
            "web-client-id",
            "web-secret",
            "https://web.example/callback",
            "desktop-client-id",
            "desktop-secret",
            "https://desktop.example/callback",
            "mobile-client-id",
            "",
            "com.decibel:/oauth2redirect");

    @Test
    void resolveClientConfig_whenDeviceTypeIsNull_defaultsToWeb() {
        GoogleClientConfig config = ReflectionTestUtils.invokeMethod(service, "resolveClientConfig", (DeviceType) null);

        assertEquals("google-web", config.name());
        assertEquals("web-client-id", config.clientId());
    }

    @Test
    void resolveClientConfig_whenDeviceTypeIsWeb_returnsWebClient() {
        GoogleClientConfig config = ReflectionTestUtils.invokeMethod(service, "resolveClientConfig", DeviceType.WEB);

        assertEquals("google-web", config.name());
        assertEquals("web-client-id", config.clientId());
    }

    @Test
    void resolveClientConfig_whenDeviceTypeIsDesktop_returnsDesktopClient() {
        GoogleClientConfig config = ReflectionTestUtils.invokeMethod(service, "resolveClientConfig", DeviceType.DESKTOP);

        assertEquals("google-desktop", config.name());
        assertEquals("desktop-client-id", config.clientId());
    }

    @Test
    void resolveClientConfig_whenDeviceTypeIsMobile_returnsMobileClient() {
        GoogleClientConfig config = ReflectionTestUtils.invokeMethod(service, "resolveClientConfig", DeviceType.MOBILE);

        assertEquals("google-mobile", config.name());
        assertEquals("mobile-client-id", config.clientId());
    }

    @Test
    void resolveClientConfig_whenDeviceTypeIsTablet_returnsMobileClient() {
        GoogleClientConfig config = ReflectionTestUtils.invokeMethod(service, "resolveClientConfig", DeviceType.TABLET);

        assertEquals("google-mobile", config.name());
        assertEquals("mobile-client-id", config.clientId());
    }

    @Test
    void resolveClientConfig_whenMappedClientIsMissing_throwsInvalidGoogleTokenException() {
        GoogleTokenVerificationService webOnlyService = new GoogleTokenVerificationService(
                RestClient.builder(),
                "web-client-id",
                "web-secret",
                "https://web.example/callback",
                "",
                "",
                "",
                "",
                "",
                "");

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(webOnlyService, "resolveClientConfig", DeviceType.DESKTOP));

        assertEquals("Google OAuth is not configured for client google-desktop", exception.getMessage());
    }

    @Test
    void resolveClientConfig_whenWebClientIsMissing_throwsInvalidGoogleTokenException() {
        GoogleTokenVerificationService nonWebService = new GoogleTokenVerificationService(
                RestClient.builder(),
                "",
                "",
                "",
                "desktop-client-id",
                "desktop-secret",
                "https://desktop.example/callback",
                "mobile-client-id",
                "",
                "com.decibel:/oauth2redirect");

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(nonWebService, "resolveClientConfig", DeviceType.WEB));

        assertEquals("Google OAuth is not configured for client google-web", exception.getMessage());
    }

    @Test
    void validateTokenInfo_whenTokenInfoIsValid_acceptsGoogleIssuerWithoutScheme() {
        GoogleTokenInfoResponse tokenInfo = validTokenInfo("web-client-id", "accounts.google.com",
                Instant.now().plusSeconds(300).getEpochSecond());

        ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient());
    }

    @Test
    void validateTokenInfo_whenTokenInfoUsesHttpsIssuer_acceptsToken() {
        GoogleTokenInfoResponse tokenInfo = validTokenInfo("web-client-id", "https://accounts.google.com",
                Instant.now().plusSeconds(300).getEpochSecond());

        ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient());
    }

    @Test
    void validateTokenInfo_whenSubjectMissing_throwsInvalidGoogleTokenException() {
        GoogleTokenInfoResponse tokenInfo = new GoogleTokenInfoResponse(
                " ",
                "user@example.com",
                "true",
                "User",
                "avatar.png",
                "web-client-id",
                "accounts.google.com",
                String.valueOf(Instant.now().plusSeconds(300).getEpochSecond()));

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient()));

        assertEquals("Google token subject is missing.", exception.getMessage());
    }

    @Test
    void validateTokenInfo_whenEmailMissing_throwsInvalidGoogleTokenException() {
        GoogleTokenInfoResponse tokenInfo = new GoogleTokenInfoResponse(
                "subject-123",
                " ",
                "true",
                "User",
                "avatar.png",
                "web-client-id",
                "accounts.google.com",
                String.valueOf(Instant.now().plusSeconds(300).getEpochSecond()));

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient()));

        assertEquals("Google token email is missing.", exception.getMessage());
    }

    @Test
    void validateTokenInfo_whenAudienceDoesNotMatchSelectedClient_throwsInvalidGoogleTokenException() {
        GoogleTokenInfoResponse tokenInfo = validTokenInfo("desktop-client-id", "accounts.google.com",
                Instant.now().plusSeconds(300).getEpochSecond());

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient()));

        assertEquals("Google token audience is invalid.", exception.getMessage());
    }

    @Test
    void validateTokenInfo_whenIssuerIsUnexpected_throwsInvalidGoogleTokenException() {
        GoogleTokenInfoResponse tokenInfo = validTokenInfo("web-client-id", "malicious.example.com",
                Instant.now().plusSeconds(300).getEpochSecond());

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient()));

        assertEquals("Google token issuer is invalid.", exception.getMessage());
    }

    @Test
    void validateTokenInfo_whenTokenIsExpired_throwsInvalidGoogleTokenException() {
        GoogleTokenInfoResponse tokenInfo = validTokenInfo("web-client-id", "accounts.google.com",
                Instant.now().minusSeconds(10).getEpochSecond());

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient()));

        assertEquals("Google token is expired.", exception.getMessage());
    }

    @Test
    void validateTokenInfo_whenExpiryIsNotNumeric_throwsInvalidGoogleTokenException() {
        GoogleTokenInfoResponse tokenInfo = new GoogleTokenInfoResponse(
                "subject-123",
                "user@example.com",
                "true",
                "User",
                "avatar.png",
                "web-client-id",
                "accounts.google.com",
                "not-a-number");

        InvalidGoogleTokenException exception = assertThrows(
                InvalidGoogleTokenException.class,
                () -> ReflectionTestUtils.invokeMethod(service, "validateTokenInfo", tokenInfo, webClient()));

        assertEquals("Google token expiry is invalid.", exception.getMessage());
    }

    private GoogleClientConfig webClient() {
        return new GoogleClientConfig("google-web", "web-client-id", "web-secret", "https://web.example/callback");
    }

    private GoogleTokenInfoResponse validTokenInfo(String audience, String issuer, long expiresAtEpochSeconds) {
        return new GoogleTokenInfoResponse(
                "subject-123",
                "user@example.com",
                "true",
                "User",
                "avatar.png",
                audience,
                issuer,
                String.valueOf(expiresAtEpochSeconds));
    }
}
