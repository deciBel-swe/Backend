package software.decibel.services.auth;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.google.GoogleClientConfig;
import software.decibel.dtos.auth.google.GoogleTokenInfoResponse;
import software.decibel.dtos.auth.google.GoogleTokenResponse;
import software.decibel.dtos.auth.google.VerifiedGoogleToken;
import software.decibel.enums.DeviceType;
import software.decibel.exceptions.custom.InvalidGoogleTokenException;

@Service
public class GoogleTokenVerificationService {

    private static final String GOOGLE_TOKEN_INFO_BASE_URL = "https://oauth2.googleapis.com";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_ISSUER = "accounts.google.com";
    private static final String GOOGLE_ISSUER_HTTPS = "https://accounts.google.com";

    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerificationService.class);

    private final RestClient restClient;
    private final List<GoogleClientConfig> googleClients;

    public GoogleTokenVerificationService(
            RestClient.Builder restClientBuilder,
            @Value("${spring.security.oauth2.client.registration.google-web.client-id:}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.google-web.client-secret:}") String googleClientSecret,
            @Value("${spring.security.oauth2.client.registration.google-web.redirect-uri:}") String googleRedirectUri,
            @Value("${spring.security.oauth2.client.registration.google-desktop.client-id:}") String googleDesktopClientId,
            @Value("${spring.security.oauth2.client.registration.google-desktop.client-secret:}") String googleDesktopClientSecret,
            @Value("${spring.security.oauth2.client.registration.google-desktop.redirect-uri:}") String googleDesktopRedirectUri,
            @Value("${spring.security.oauth2.client.registration.google-mobile.client-id:}") String googleMobileClientId,
            @Value("${spring.security.oauth2.client.registration.google-mobile.client-secret:}") String googleMobileClientSecret,
            @Value("${spring.security.oauth2.client.registration.google-mobile.redirect-uri:}") String googleMobileRedirectUri) {
        this.restClient = restClientBuilder.baseUrl(GOOGLE_TOKEN_INFO_BASE_URL).build();
        this.googleClients = List.of(
                new GoogleClientConfig("google-web", googleClientId, googleClientSecret, googleRedirectUri),
                new GoogleClientConfig("google-desktop", googleDesktopClientId, googleDesktopClientSecret,
                        googleDesktopRedirectUri),
                new GoogleClientConfig("google-mobile", googleClientId, googleClientSecret,
                        googleRedirectUri))
                .stream()
                .filter(client -> StringUtils.hasText(client.clientId()))
                .toList();
    }

    public VerifiedGoogleToken verifyAuthCode(String authCode) {
        return verifyAuthCode(authCode, null);
    }

    public VerifiedGoogleToken verifyAuthCode(String authCode, DeviceInfo deviceInfo) {
        log.info("verifyAuthCode called with authCode={}", authCode);

        GoogleClientConfig client = resolveClientConfig(deviceInfo == null ? null : deviceInfo.deviceType());
        String idToken = exchangeAuthCodeForIdToken(authCode, client);
        GoogleTokenInfoResponse tokenInfo = fetchTokenInfo(idToken);
        validateTokenInfo(tokenInfo, client);

        return new VerifiedGoogleToken(
                tokenInfo.subject(),
                tokenInfo.email(),
                "true".equalsIgnoreCase(tokenInfo.emailVerified()),
                tokenInfo.name(),
                tokenInfo.picture());
    }

    private String exchangeAuthCodeForIdToken(String authCode, GoogleClientConfig client) {
        String decodedAuthCode;
        try {
            decodedAuthCode = java.net.URLDecoder.decode(authCode, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            decodedAuthCode = authCode;
        }

        log.info("exchangeAuthCodeForIdToken called with authCode={} using client={}", decodedAuthCode, client.name());
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", authCode);
            params.add("client_id", client.clientId());
            params.add("grant_type", "authorization_code");

            if (StringUtils.hasText(client.redirectUri())) {
                params.add("redirect_uri", client.redirectUri());
            }

            if (StringUtils.hasText(client.clientSecret())) {
                params.add("client_secret", client.clientSecret());
            }

            GoogleTokenResponse tokenResponse = RestClient.create()
                    .post()
                    .uri(GOOGLE_TOKEN_ENDPOINT)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .body(params)
                    .retrieve()
                    .body(GoogleTokenResponse.class);

            if (tokenResponse == null || tokenResponse.idToken() == null) {
                throw new InvalidGoogleTokenException("Failed to exchange auth code for ID token.");
            }

            return tokenResponse.idToken();
        } catch (RestClientException ex) {
            log.error("Google token exchange failed: {}", ex.getMessage(), ex);
            throw new InvalidGoogleTokenException("Failed to exchange Google auth code.", ex);
        }
    }

    private GoogleClientConfig resolveClientConfig(DeviceType deviceType) {
        String clientName = switch (deviceType == null ? DeviceType.WEB : deviceType) {
            case DESKTOP ->
                "google-desktop";
            case MOBILE, TABLET ->
                "google-mobile";
            case WEB ->
                "google-web";
        };

        return googleClients.stream()
                .filter(client -> client.name().equals(clientName))
                .findFirst()
                .orElseThrow(() -> new InvalidGoogleTokenException(
                "Google OAuth is not configured for client " + clientName));
    }

    private GoogleTokenInfoResponse fetchTokenInfo(String idToken) {
        try {
            GoogleTokenInfoResponse tokenInfo = restClient.get()
                    .uri(uriBuilder -> uriBuilder.path("/tokeninfo").queryParam("id_token", idToken).build())
                    .retrieve()
                    .body(GoogleTokenInfoResponse.class);

            if (tokenInfo == null) {
                throw new InvalidGoogleTokenException("Invalid Google token.");
            }

            return tokenInfo;
        } catch (RestClientException ex) {
            throw new InvalidGoogleTokenException("Invalid Google token.", ex);
        }
    }

    private void validateTokenInfo(GoogleTokenInfoResponse tokenInfo, GoogleClientConfig client) {
        if (tokenInfo.subject() == null || tokenInfo.subject().isBlank()) {
            throw new InvalidGoogleTokenException("Google token subject is missing.");
        }

        if (tokenInfo.email() == null || tokenInfo.email().isBlank()) {
            throw new InvalidGoogleTokenException("Google token email is missing.");
        }

        if (!client.clientId().equals(tokenInfo.audience())) {
            throw new InvalidGoogleTokenException("Google token audience is invalid.");
        }

        if (!GOOGLE_ISSUER.equals(tokenInfo.issuer()) && !GOOGLE_ISSUER_HTTPS.equals(tokenInfo.issuer())) {
            throw new InvalidGoogleTokenException("Google token issuer is invalid.");
        }

        if (isExpired(tokenInfo.expiresAtEpochSeconds())) {
            throw new InvalidGoogleTokenException("Google token is expired.");
        }
    }

    private boolean isExpired(String expiresAtEpochSeconds) {
        try {
            long expiresAt = Long.parseLong(expiresAtEpochSeconds);
            return expiresAt <= Instant.now().getEpochSecond();
        } catch (NumberFormatException ex) {
            throw new InvalidGoogleTokenException("Google token expiry is invalid.", ex);
        }
    }
}
