package software.decibel.services;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class SmtpEmailService implements EmailService {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailService.class);
    private final JavaMailSender mailSender;

    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        String html = loadTemplate("email-password-reset.html")
                .replace("{{VERIFY_URL}}", resetLink);
        sendEmail(toEmail, "Reset your DeciBel password", html);
        log.info("password reset email to={} subject='Reset your DeciBel password' link={}", toEmail, resetLink);
    }

    @Override
    public void sendEmailVerificationEmail(String toEmail, String verificationLink) {
        String html = loadTemplate("email-verification.html")
                .replace("{{VERIFY_URL}}", verificationLink);
        sendEmail(toEmail, "Verify your DeciBel email", html);
        log.info(
                "email verification email to={} subject='Verify your DeciBel email' link={}",
                toEmail,
                verificationLink);
    }

    @Override
    public void sendEmailChangeVerificationEmail(String toEmail, String verificationLink) {
        String html = loadTemplate("email-change.html")
                .replace("{{VERIFY_URL}}", verificationLink);
        sendEmail(toEmail, "Confirm your new DeciBel email", html);
        log.info(
                "email change verification email to={} subject='Confirm your new DeciBel email' link={}",
                toEmail,
                verificationLink);
    }

    private void sendEmail(String toEmail, String subject, String htmlBody) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlBody, true); // true = html
            mailSender.send(message);
            log.info("Email sent to={} subject='{}'", toEmail, subject);
        } catch (MessagingException e) {
            log.error("Failed to send email to={} subject='{}': {}", toEmail, subject, e.getMessage());
            throw new RuntimeException("Failed to send email to " + toEmail, e);
        }
    }

    private String loadTemplate(String templateName) {
        try {
            ClassPathResource resource = new ClassPathResource("templates/" + templateName);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load template: " + templateName, e);
        }
    }
}
