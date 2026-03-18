package software.decibel.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import software.decibel.dtos.auth.google.GoogleTokenInfoResponse;
import software.decibel.dtos.auth.google.VerifiedGoogleToken;
import software.decibel.exceptions.custom.ExternalAuthConfigurationException;
import software.decibel.exceptions.custom.InvalidGoogleTokenException;

import java.time.Instant;

@Service
public class GoogleTokenVerificationService {

    private static final String GOOGLE_TOKEN_INFO_BASE_URL = "https://oauth2.googleapis.com";
    private static final String GOOGLE_ISSUER = "accounts.google.com";
    private static final String GOOGLE_ISSUER_HTTPS = "https://accounts.google.com";

    private final RestClient restClient;
    private final String googleClientId;

    public GoogleTokenVerificationService(
            RestClient.Builder restClientBuilder,
            @Value("${spring.security.oauth2.client.registration.google.client-id:}") String googleClientId
    ) {
        this.restClient = restClientBuilder.baseUrl(GOOGLE_TOKEN_INFO_BASE_URL).build();
        this.googleClientId = googleClientId;
    }

    public VerifiedGoogleToken verifyIdToken(String idToken) {
        if (googleClientId == null || googleClientId.isBlank()) {
            throw new ExternalAuthConfigurationException(
                    "Google client ID must be configured before verifying Google tokens.");
        }

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


