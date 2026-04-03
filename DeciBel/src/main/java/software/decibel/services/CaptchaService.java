package software.decibel.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.auth.CaptchaResponse;
import software.decibel.exceptions.custom.CaptchaValidationException;

@Slf4j
@Service
public class CaptchaService {

    private static final String RECAPTCHA_VERIFY_URL = "https://www.google.com/recaptcha/api/siteverify";
    private static final double MIN_SCORE = 0.5; // 0.0 = bot, 1.0 = human

    @Value("${captcha.bypass-token:}") // put empty in production
    private String bypassToken;

    private final RestClient restClient;
    private final String recaptchaSecretKey;

    public CaptchaService(
            RestClient.Builder restClientBuilder,
            @Value("${app.recaptcha.secret-key:}") String recaptchaSecretKey) {
        this.restClient = restClientBuilder.baseUrl(RECAPTCHA_VERIFY_URL).build();
        this.recaptchaSecretKey = recaptchaSecretKey;
    }

    public void validateCaptcha(String captchaToken) {
        if (!bypassToken.isEmpty() && bypassToken.equals(captchaToken)) {
            return; // Success! Skip Google verification.
        }
        // Skip validation in local/dev if secret key is not configured
        if (recaptchaSecretKey == null || recaptchaSecretKey.isBlank()) {
            log.warn("reCAPTCHA secret key not configured — skipping captcha validation");
            return;
        }

        CaptchaResponse response = restClient.post()
                .uri(uriBuilder -> uriBuilder
                .queryParam("secret", recaptchaSecretKey)
                .queryParam("response", captchaToken)
                .build())
                .retrieve()
                .body(CaptchaResponse.class);

        if (response == null || !response.success()) {
            throw new CaptchaValidationException("Captcha verification failed.");
        }

        if (response.score() < MIN_SCORE) {
            throw new CaptchaValidationException("Captcha score too low. Possible bot activity detected.");
        }

        log.info("Captcha validated successfully. score={} action={}", response.score(), response.action());
    }
}
