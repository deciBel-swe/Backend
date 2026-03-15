package software.decibel.controllers;

import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.LoginLocalResponse;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.services.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    private final String activeProfile;

    public AuthController(AuthService authService, @org.springframework.beans.factory.annotation.Value("${spring.profiles.active:default}") String activeProfile) {
        this.authService = authService;
        this.activeProfile = activeProfile;
    }

    @PostMapping("/register/local")
    public ResponseEntity<MessageResponse> registerLocal(@Valid @RequestBody RegisterLocalRequest request) {
        MessageResponse response = authService.registerLocal(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login/local")
    public ResponseEntity<LoginLocalResponse> loginLocal(@Valid @RequestBody LoginLocalRequest request) {
        AuthService.AuthLoginResult result = authService.loginLocal(request);
        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken(), result.refreshTokenExpiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        AuthService.AuthRefreshTokenResult result = authService.verifyEmail(request);
        // TODO: Still need to discuss Token issuing strategy for email verification flow. For now, reusing refresh token mechanism to set cookie and frontend can discard it immediately after reading the verification success message.
        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken(), result.refreshTokenExpiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new MessageResponse("Email verified"));
    }

    @GetMapping("/oauth2/authorization/google")
    public ResponseEntity<Void> triggerGoogleLogin() {
        // Placeholder endpoint definition for the Google OAuth entry point.
        // The final implementation should be handled by Spring Security OAuth2 client configuration.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    @GetMapping("/login/oauth2/code/google")
    public ResponseEntity<Void> googleCallback() {
        // Placeholder endpoint definition for the Google OAuth callback.
        // The final implementation should exchange the Google callback through Spring Security and then run the success handler.
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    private ResponseCookie buildRefreshCookie(String refreshToken, long maxAgeSeconds) {
        boolean isProduction = !"default".equals(activeProfile) && !"local".equals(activeProfile) && !"dev".equals(activeProfile);
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true) // Prevent JavaScript access to mitigate XSS
                .secure(isProduction)
                .sameSite("Lax") // Protection against CSRF
                .path("/auth") // Limit cookie scope to auth endpoints
                .maxAge(maxAgeSeconds)
                .build();
    }
}
