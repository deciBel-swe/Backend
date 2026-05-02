package software.decibel.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.auth.CaptchaRequest;
import software.decibel.dtos.auth.CaptchaResponse;
import software.decibel.exceptions.custom.CaptchaValidationException;

@Slf4j
@Service
public class CaptchaService {

    private static final double MIN_SCORE = 0.5;

    private final String bypassToken;
    private final String bypassEmailDomain;
    private final String projectId;
    private final String apiKey;
    private final String siteKey;
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public CaptchaService(
            RestClient.Builder restClientBuilder,
            @Value("${captcha.bypass-token:}") String bypassToken,
            @Value("${email.bypass-domain:}") String bypassEmailDomain,
            @Value("${google.recaptcha.enterprise.project-id:}") String projectId,
            @Value("${google.recaptcha.enterprise.api-key:}") String apiKey,
            @Value("${google.recaptcha.enterprise.site-key:}") String siteKey) {

        this.bypassToken = bypassToken;
        this.bypassEmailDomain = bypassEmailDomain;
        this.projectId = projectId;
        this.apiKey = apiKey;
        this.siteKey = siteKey;

        // Base URL for the Enterprise REST API
        this.restClient = restClientBuilder
                .baseUrl("https://recaptchaenterprise.googleapis.com/v1")
                .build();
    }

    public void validateCaptcha(String captchaToken, String email) {
        String expectedAction = "register_local";

        if (bypassToken != null && !bypassToken.isBlank() && bypassToken.equals(captchaToken)) {
            log.debug("Captcha bypassed using configured bypass token.");
            return;
        }

        if (bypassEmailDomain != null && !bypassEmailDomain.isBlank()
                && email != null && email.endsWith("@" + bypassEmailDomain)) {
            log.debug("Captcha bypassed for test email domain: {}", bypassEmailDomain);
            return;
        }

        if (projectId.isBlank() || apiKey.isBlank() || siteKey.isBlank()) {
            log.warn("reCAPTCHA Enterprise credentials not fully configured — skipping validation");
            return;
        }

        try {
            var requestBody = new CaptchaRequest(
                    new CaptchaRequest.Event(captchaToken, siteKey, expectedAction)
            );

            String rawResponse = restClient.post()
                    .uri("/projects/{projectId}/assessments?key={apiKey}", projectId, apiKey)
                    .body(requestBody)
                    .retrieve()
                    .body(String.class);

            log.info("RAW GOOGLE RESPONSE: {}", rawResponse);

            if (rawResponse == null || rawResponse.isBlank()) {
                throw new CaptchaValidationException("Empty response from reCAPTCHA Enterprise.");
            }

            CaptchaResponse response = objectMapper.readValue(rawResponse, CaptchaResponse.class);

            if (response.tokenProperties() == null) {
                throw new CaptchaValidationException("Missing tokenProperties in reCAPTCHA response.");
            }

            if (!response.tokenProperties().valid()) {
                log.warn("Captcha invalid. Reason: {}", response.tokenProperties().invalidReason());
                throw new CaptchaValidationException("Captcha verification failed.");
            }

            if (!expectedAction.equals(response.tokenProperties().action())) {
                log.warn("Captcha action mismatch. Expected: {}, Got: {}",
                        expectedAction, response.tokenProperties().action());
                throw new CaptchaValidationException("Captcha action mismatch.");
            }

            Double score = response.riskAnalysis() != null ? response.riskAnalysis().score() : null;
            if (score == null || score < MIN_SCORE) {
                log.warn("Captcha score too low: {}", score);
                throw new CaptchaValidationException("Captcha score too low. Possible bot activity detected.");
            }

            log.info("Enterprise Captcha validated successfully. score={} action={}", score, expectedAction);

        } catch (RestClientException e) {
            log.error("Failed to connect to Google reCAPTCHA Enterprise service", e);
            throw new CaptchaValidationException("Unable to verify Captcha at this time.");
        } catch (Exception e) {
            log.error("Unexpected error during reCAPTCHA validation", e);
            throw new CaptchaValidationException("Captcha validation encountered an unexpected error.");
        }
    }
}
