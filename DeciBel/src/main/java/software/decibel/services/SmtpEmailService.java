package software.decibel.services;

import org.springframework.stereotype.Service;

@Service
public class SmtpEmailService implements EmailService {
//TODO: stub to be removed and real service to replace after username and password
    @Override
    public void sendPasswordResetEmail(String toEmail, String resetLink) {
        System.out.println("=== STUB PASSWORD RESET EMAIL ===");
        System.out.println("To: " + toEmail);
        System.out.println("Subject: Reset your DeciBel password");
        System.out.println("Reset link: " + resetLink);
        System.out.println("=================================");
    }
}