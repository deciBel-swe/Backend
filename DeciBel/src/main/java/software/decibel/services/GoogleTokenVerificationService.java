package software.decibel.services;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import software.decibel.dtos.auth.google.GoogleTokenInfoResponse;
import software.decibel.dtos.auth.google.GoogleTokenResponse;
import software.decibel.dtos.auth.google.VerifiedGoogleToken;
import software.decibel.exceptions.custom.InvalidGoogleTokenException;

@Service
public class GoogleTokenVerificationService {

    private static final String GOOGLE_TOKEN_INFO_BASE_URL = "https://oauth2.googleapis.com";
    private static final String GOOGLE_TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";
    private static final String GOOGLE_ISSUER = "accounts.google.com";
    private static final String GOOGLE_ISSUER_HTTPS = "https://accounts.google.com";

    private final RestClient restClient;
    private final String googleClientId;
    private final String googleClientSecret;
    private final String googleRedirectUri;
    private static final Logger log = LoggerFactory.getLogger(GoogleTokenVerificationService.class);

    public GoogleTokenVerificationService(
            RestClient.Builder restClientBuilder,
            @Value("${spring.security.oauth2.client.registration.google-web.client-id:}") String googleClientId,
            @Value("${spring.security.oauth2.client.registration.google-web.client-secret:}") String googleClientSecret,
            @Value("${app.google.redirect-uri:}") String googleRedirectUri
    ) {
        this.restClient = restClientBuilder.baseUrl(GOOGLE_TOKEN_INFO_BASE_URL).build();
        this.googleClientId = googleClientId;
        this.googleClientSecret = googleClientSecret;
        this.googleRedirectUri = googleRedirectUri;
    }

    // Main entry point — accepts an auth code and returns verified user info
    public VerifiedGoogleToken verifyAuthCode(String authCode) {
        log.info("verifyAuthCode called with authCode={}", authCode);
        log.info("clientId={} secretBlank={} redirectUri={}",
                googleClientId,
                googleClientSecret == null || googleClientSecret.isBlank(),
                googleRedirectUri);

        // Step 1 — exchange auth code for ID token
        String idToken = exchangeAuthCodeForIdToken(authCode);

        // Step 2 — verify the ID token
        GoogleTokenInfoResponse tokenInfo = fetchTokenInfo(idToken);
        validateTokenInfo(tokenInfo);

        return new VerifiedGoogleToken(
                tokenInfo.subject(),
                tokenInfo.email(),
                "true".equalsIgnoreCase(tokenInfo.emailVerified()),
                tokenInfo.name(),
                tokenInfo.picture()
        );
    }

    // Exchanges auth code for tokens and returns the ID token
    private String exchangeAuthCodeForIdToken(String authCode) {
        String decodedAuthCode;
        try {
            decodedAuthCode = java.net.URLDecoder.decode(authCode, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            decodedAuthCode = authCode;
        }
        log.info("exchangeAuthCodeForIdToken called with authCode={}", decodedAuthCode);
        log.info("clientId={} secretBlank={} redirectUri={}",
                googleClientId,
                googleClientSecret == null || googleClientSecret.isBlank(),
                googleRedirectUri);
        try {
            MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
            params.add("code", authCode);
            params.add("client_id", googleClientId);
            params.add("redirect_uri", googleRedirectUri);
            params.add("grant_type", "authorization_code");

            // Only add client_secret if it is configured (web clients require it, installed/mobile clients do not)
            if (googleClientSecret != null && !googleClientSecret.isBlank()) {
                params.add("client_secret", googleClientSecret);
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

    private void validateTokenInfo(GoogleTokenInfoResponse tokenInfo) {
        if (tokenInfo.subject() == null || tokenInfo.subject().isBlank()) {
            throw new InvalidGoogleTokenException("Google token subject is missing.");
        }

        if (tokenInfo.email() == null || tokenInfo.email().isBlank()) {
            throw new InvalidGoogleTokenException("Google token email is missing.");
        }

        if (!googleClientId.equals(tokenInfo.audience())) {
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
