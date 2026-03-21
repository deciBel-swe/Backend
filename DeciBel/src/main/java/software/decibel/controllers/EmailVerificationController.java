package software.decibel.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.services.AuthService;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class EmailVerificationController {

    private final AuthService authService;

    // GET endpoint so the user can click the link directly from their email
    @GetMapping(value = "/verify-email-redirect", produces = MediaType.TEXT_HTML_VALUE)
    public String verifyEmailRedirect(@RequestParam String token) {
        try {
            authService.verifyEmail(new VerifyEmailRequest(token));
            return """
                    <html>
                    <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                        <h2 style="color: #6200ea;">Email Verified!</h2>
                        <p>Your DeciBel account has been verified. You can now log in.</p>
                    </body>
                    </html>
                    """;
        } catch (Exception e) {
            return """
                    <html>
                    <body style="font-family: Arial, sans-serif; text-align: center; padding: 50px;">
                        <h2 style="color: #e53935;">Verification Failed</h2>
                        <p>The link is invalid or has expired. Please register again.</p>
                    </body>
                    </html>
                    """;
        }
    }
}
