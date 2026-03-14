package software.decibel.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {
    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        // TODO: replace stub logging with real SMTP delivery once credentials are
        // configured.
        log.info("Stub password reset email to={} subject='Reset your DeciBel password' link={}", toEmail, resetLink);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String verificationLink) {
        // TODO: replace stub logging with real SMTP delivery once credentials are
        // configured.
        log.info(
                "Stub email verification email to={} subject='Verify your DeciBel email' link={}",
                toEmail,
                verificationLink);
    }

    @Override
    public void sendEmailChangeVerificationEmail(String toEmail, String verificationLink) {
        // TODO: replace stub logging with real SMTP delivery once credentials are
        // configured.
        log.info(
                "Stub email change verification email to={} subject='Confirm your new DeciBel email' link={}",
                toEmail,
                verificationLink);
    }
}
