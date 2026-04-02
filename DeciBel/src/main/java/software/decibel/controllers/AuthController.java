package software.decibel.controllers;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.view.RedirectView;

import jakarta.validation.Valid;
import software.decibel.dtos.auth.AuthLoginResult;
import software.decibel.dtos.auth.AuthTokenRotationResult;
import software.decibel.dtos.auth.GoogleOauthRequest;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.LoginLocalResponse;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RefreshTokenResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.dtos.auth.google.ResendVerificationEmailRequest;
import software.decibel.services.AuthService;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    private String activeProfile;
    @Value("${app.google.redirect-uri}")
    private String googleRedirectUri;
    @Value("${spring.security.oauth2.client.registration.google.client-id}")
    private String googleClientId;

    public AuthController(AuthService authService,
            @Value("${spring.profiles.active:default}") String activeProfile) {
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
        AuthLoginResult result = authService.loginLocal(request);
        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken(), result.refreshTokenExpiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/logout")
    public ResponseEntity<MessageResponse> logout(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        // If the cookie is missing, we can't invalidate the session, 
        // but we should still clear the cookie just in case.
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logout(refreshToken);
        }

        ResponseCookie refreshCookie = buildRefreshCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new MessageResponse("Logged out successfully"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<MessageResponse> logoutAll(@CookieValue(name = "refreshToken", required = false) String refreshToken) {
        if (refreshToken != null && !refreshToken.isBlank()) {
            authService.logoutAll(refreshToken);
        }

        ResponseCookie refreshCookie = buildRefreshCookie("", 0);
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(new MessageResponse("Logged out from all sessions successfully"));
    }

    @PostMapping("/verify-email")
    public ResponseEntity<MessageResponse> verifyEmail(@Valid @RequestBody VerifyEmailRequest request) {
        MessageResponse response = authService.verifyEmail(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/oauth/google")
    public ResponseEntity<LoginLocalResponse> exchangeGoogleOauthToken(
            @Valid @RequestBody GoogleOauthRequest request) {
        AuthLoginResult result = authService.loginWithGoogle(request);
        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken(), result.refreshTokenExpiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/refreshtoken")
    public ResponseEntity<RefreshTokenResponse> refreshToken(@CookieValue(name = "refreshToken") String refreshToken) {
        AuthTokenRotationResult result = authService.refreshToken(refreshToken);
        ResponseCookie refreshCookie = buildRefreshCookie(result.refreshToken(), result.refreshTokenExpiresIn());
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE, refreshCookie.toString())
                .body(result.response());
    }

    @PostMapping("/resend-verification")
    public ResponseEntity<MessageResponse> resendVerificationEmail(
            @Valid @RequestBody ResendVerificationEmailRequest request) {
        return ResponseEntity.ok(authService.resendVerificationEmail(request));
    }

    @GetMapping("/login/oauth2/code/google")
    public RedirectView redirectToGoogle() {
        String googleAuthUrl = "https://accounts.google.com/o/oauth2/v2/auth"
                + "?client_id=" + googleClientId
                + "&redirect_uri=" + googleRedirectUri
                + "&response_type=code"
                + "&scope=openid%20email%20profile"
                + "&access_type=offline";

        return new RedirectView(googleAuthUrl);
    }

    private ResponseCookie buildRefreshCookie(String refreshToken, long maxAgeSeconds) {
        boolean isProduction = !"default".equals(activeProfile) && !"local".equals(activeProfile)
                && !"dev".equals(activeProfile);
        return ResponseCookie.from("refreshToken", refreshToken)
                .httpOnly(true) // Prevent JavaScript access to mitigate XSS
                .secure(isProduction)
                .sameSite("Lax") // Protection against CSRF
                .path("/auth") // Limit cookie scope to auth endpoints
                .maxAge(maxAgeSeconds)
                .build();
    }
}
