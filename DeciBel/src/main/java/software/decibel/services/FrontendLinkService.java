package software.decibel.services;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class FrontendLinkService {

    private final String frontendBaseUrl;

    public FrontendLinkService(@Value("${frontend.base.url:https://decibel.foo}") String frontendBaseUrl) {
        this.frontendBaseUrl = frontendBaseUrl;
    }

    public String buildPasswordResetLink(String token) {
        return frontendBaseUrl + "/change-password?token=" + token;
    }

    public String buildEmailVerificationLink(String token) {
        return frontendBaseUrl + "/verify-email?token=" + token;
    }

    public String buildEmailChangeVerificationLink(String token) {
        return frontendBaseUrl + "/verify-email-change?token=" + token;
    }
}
