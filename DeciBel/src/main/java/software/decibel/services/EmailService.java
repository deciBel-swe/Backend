package software.decibel.services;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);

    void sendEmailVerificationEmail(String toEmail, String verificationLink);

    void sendEmailChangeVerificationEmail(String toEmail, String verificationLink);
}
