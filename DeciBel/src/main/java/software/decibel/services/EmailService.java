package software.decibel.services;

public interface EmailService {
    void sendPasswordResetEmail(String toEmail, String resetLink);
}
