package software.decibel.services;

import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.GoogleOauthRequest;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.LogoutSessionRequest;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.dtos.auth.google.VerifiedGoogleToken;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.DeviceType;
import software.decibel.enums.TokenType;
import software.decibel.exceptions.custom.InvalidGoogleTokenException;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthIdentityRepository authIdentityRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private TokenService tokenService;
    @Mock
    private SessionService sessionService;
    @Mock
    private EmailService emailService;
    @Mock
    private FrontendLinkService frontendLinkService;
    @Mock
    private JwtService jwtService;
    @Mock
    private GoogleTokenVerificationService googleTokenVerificationService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerLocal_whenRequestIsValid_savesUserAndSendsEmail() {
        RegisterLocalRequest request = registerRequest();
        when(authIdentityRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(anyString(), any(), any()))
                .thenReturn(false);
        when(userRepository.findByUsername(request.username())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");

        User savedUser = User.builder().id(7L).username(request.username()).build();
        when(userRepository.save(any(User.class))).thenReturn(savedUser);

        Token verificationToken = Token.builder().hash("hash").build();
        when(tokenService.createEmailVerificationToken(any(User.class)))
                .thenReturn(new TokenService.IssuedToken("raw-token", verificationToken));
        when(frontendLinkService.buildEmailVerificationLink("raw-token"))
                .thenReturn("https://link.com/verify?token=raw-token");

        MessageResponse response = authService.registerLocal(request);

        assertEquals("User Generated successfully", response.message());
        verify(emailService).sendEmailVerificationEmail(eq("new@example.com"), contains("raw-token"));
        verify(userRepository).save(any(User.class));
        verify(authIdentityRepository).save(any(AuthIdentity.class));
    }

    @Test
    void registerLocal_whenEmailAlreadyExists_throwsConflict() {
        RegisterLocalRequest request = registerRequest();
        when(authIdentityRepository.existsByEmailIgnoreCase(request.email())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.registerLocal(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(authIdentityRepository, never()).save(any(AuthIdentity.class));
    }

    @Test
    void registerLocal_whenUsernameAlreadyExists_throwsConflict() {
        RegisterLocalRequest request = registerRequest();
        when(authIdentityRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(anyString(), any(), any()))
                .thenReturn(false);
        when(userRepository.findByUsername(request.username())).thenReturn(Optional.of(User.builder().build()));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.registerLocal(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(authIdentityRepository, never()).save(any(AuthIdentity.class));
    }

    @Test
    void loginLocal_whenCredentialsAreValid_returnsTokensWithCorrectRole() {
        LoginLocalRequest request = loginRequest();
        User user = verifiedUser();
        user.setId(5L);
        AuthIdentity identity = verifiedIdentity(user);

        when(authIdentityRepository.findByEmailIgnoreCaseAndProviderAndType(anyString(), any(), any()))
                .thenReturn(Optional.of(identity));
        when(passwordEncoder.matches(request.password(), identity.getPasswordHash())).thenReturn(true);

        // Mock TokenService
        Token mockToken = Token.builder().hash("hash").build();
        when(tokenService.createRefreshToken(user))
                .thenReturn(new TokenService.IssuedToken("refresh-token", mockToken));

        // Mock JwtService - Ensures Role (ARTIST) is mapped!
        when(jwtService.buildAccessToken(user, identity.getEmail())).thenReturn("access-token");

        AuthService.AuthLoginResult result = authService.loginLocal(request);

        assertEquals("access-token", result.response().accessToken());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(AccountTier.PRO, result.response().user().tier()); // Role verified!
        verify(tokenService).createRefreshToken(user);
        verify(sessionService).createSession(user, mockToken, request.deviceInfo());
    }

    @Test
    void loginLocal_whenEmailIsNotVerified_throwsForbidden() {
        LoginLocalRequest request = loginRequest();
        User user = verifiedUser();
        AuthIdentity identity = verifiedIdentity(user);
        identity.setEmailVerified(false);

        when(authIdentityRepository.findByEmailIgnoreCaseAndProviderAndType(anyString(), any(), any()))
                .thenReturn(Optional.of(identity));
        when(passwordEncoder.matches(request.password(), identity.getPasswordHash())).thenReturn(true);

        assertThrows(ResponseStatusException.class, () -> authService.loginLocal(request));
        verify(tokenService, never()).createRefreshToken(any());
    }

    @Test
    void loginLocal_whenIdentityDoesNotExist_throwsUnauthorized() {
        LoginLocalRequest request = loginRequest();
        when(authIdentityRepository.findByEmailIgnoreCaseAndProviderAndType(anyString(), any(), any()))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.loginLocal(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(passwordEncoder, never()).matches(anyString(), anyString());
        verify(tokenService, never()).createRefreshToken(any());
    }

    @Test
    void loginLocal_whenPasswordIsInvalid_throwsUnauthorized() {
        LoginLocalRequest request = loginRequest();
        User user = verifiedUser();
        AuthIdentity identity = verifiedIdentity(user);

        when(authIdentityRepository.findByEmailIgnoreCaseAndProviderAndType(anyString(), any(), any()))
                .thenReturn(Optional.of(identity));
        when(passwordEncoder.matches(request.password(), identity.getPasswordHash())).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.loginLocal(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(tokenService, never()).createRefreshToken(any());
    }

    @Test
    void verifyEmail_whenTokenIsValid_marksUserVerified() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        User user = User.builder().id(9L).username("verified-user").build();
        AuthIdentity identity = AuthIdentity.builder().user(user).emailVerified(false).build();
        Token verificationToken = Token.builder().user(user).build();

        when(tokenService.findValidUnusedToken(eq("raw-token"), any(), anyString()))
                .thenReturn(verificationToken);
        when(authIdentityRepository.findByUserAndProviderAndType(any(), any(), any()))
                .thenReturn(Optional.of(identity));

        Token mockToken = Token.builder().hash("hash").build();
        when(tokenService.createRefreshToken(user))
                .thenReturn(new TokenService.IssuedToken("refresh-token", mockToken));

        authService.verifyEmail(request);

        assertTrue(identity.isEmailVerified());
        verify(userRepository, never()).save(any(User.class));
        verify(tokenService).markTokenUsed(verificationToken);
        verify(sessionService, never()).createSession(any(), any(), any());
    }

    @Test
    void verifyEmail_whenLocalIdentityDoesNotExist_stillMarksTokenAndIssuesRefreshToken() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        User user = User.builder().id(9L).username("verified-user").build();
        Token verificationToken = Token.builder().user(user).build();
        Token refreshToken = Token.builder().hash("hash").build();

        when(tokenService.findValidUnusedToken(eq("raw-token"), eq(TokenType.EMAIL_VERIFICATION), anyString()))
                .thenReturn(verificationToken);
        when(authIdentityRepository.findByUserAndProviderAndType(
                user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.empty());
        when(tokenService.createRefreshToken(user))
                .thenReturn(new TokenService.IssuedToken("refresh-token", refreshToken));

        AuthService.AuthRefreshTokenResult result = authService.verifyEmail(request);

        assertEquals("refresh-token", result.refreshToken());
        verify(authIdentityRepository, never()).save(any(AuthIdentity.class));
        verify(tokenService).markTokenUsed(verificationToken);
    }

    @Test
    void logout_whenRefreshTokenIsValid_deletesSessionAndToken() {
        LogoutSessionRequest request = new LogoutSessionRequest("refresh-token");
        Token refreshToken = Token.builder().hash("hash").build();

        when(tokenService.findValidUnusedToken(
                "refresh-token",
                software.decibel.enums.TokenType.REFRESH_TOKEN,
                "Invalid refresh token"))
                .thenReturn(refreshToken);

        MessageResponse response = authService.logout(request);

        assertEquals("Logged out successfully", response.message());
        verify(sessionService).deleteSessionByRefreshToken(refreshToken);
        verify(tokenService).deleteToken(refreshToken);
    }

    @Test
    void logout_whenRefreshTokenIsInvalid_propagatesExceptionAndDoesNotDeleteAnything() {
        LogoutSessionRequest request = new LogoutSessionRequest("refresh-token");
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid refresh token");

        when(tokenService.findValidUnusedToken(
                "refresh-token",
                TokenType.REFRESH_TOKEN,
                "Invalid refresh token"))
                .thenThrow(exception);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> authService.logout(request));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        verify(sessionService, never()).deleteSessionByRefreshToken(any());
        verify(tokenService, never()).deleteToken(any());
    }

    @Test
    void logoutAll_whenRefreshTokenIsValid_deletesAllUserSessionsAndRefreshTokens() {
        LogoutSessionRequest request = new LogoutSessionRequest("refresh-token");
        User user = User.builder().id(21L).username("listener").build();
        Token refreshToken = Token.builder().hash("hash").user(user).build();

        when(tokenService.findValidUnusedToken(
                "refresh-token",
                software.decibel.enums.TokenType.REFRESH_TOKEN,
                "Invalid refresh token"))
                .thenReturn(refreshToken);

        MessageResponse response = authService.logoutAll(request);

        assertEquals("Logged out of all sessions", response.message());
        verify(sessionService).deleteAllSessionsForUser(user);
        verify(tokenService).deleteTokensForUserAndType(user, software.decibel.enums.TokenType.REFRESH_TOKEN);
    }

    @Test
    void logoutAll_whenRefreshTokenIsInvalid_propagatesExceptionAndDoesNotDeleteAnything() {
        LogoutSessionRequest request = new LogoutSessionRequest("refresh-token");
        ResponseStatusException exception = new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Invalid refresh token");

        when(tokenService.findValidUnusedToken(
                "refresh-token",
                TokenType.REFRESH_TOKEN,
                "Invalid refresh token"))
                .thenThrow(exception);

        ResponseStatusException thrown = assertThrows(ResponseStatusException.class,
                () -> authService.logoutAll(request));

        assertEquals(HttpStatus.BAD_REQUEST, thrown.getStatusCode());
        verify(sessionService, never()).deleteAllSessionsForUser(any());
        verify(tokenService, never()).deleteTokensForUserAndType(any(), any());
    }

    @Test
    void refreshToken_whenValid_rotatesTokenAndReturnsNewAccessToken() {
        User user = verifiedUser();
        Token oldToken = Token.builder().user(user).hash("old-hash").build();
        Token newToken = Token.builder().user(user).hash("new-hash").build();
        AuthIdentity identity = verifiedIdentity(user);

        when(tokenService.findValidUnusedToken("old-refresh-token", TokenType.REFRESH_TOKEN,
                "Invalid refresh token"))
                .thenReturn(oldToken);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.of(identity));
        when(tokenService.createRefreshToken(user))
                .thenReturn(new TokenService.IssuedToken("new-refresh-token", newToken));
        when(jwtService.buildAccessToken(user, identity.getEmail())).thenReturn("new-access-token");

        AuthService.AuthTokenRotationResult result = authService.refreshToken("old-refresh-token");

        assertEquals("new-access-token", result.response().accessToken());
        assertEquals("new-refresh-token", result.refreshToken());
        verify(tokenService).markTokenUsed(oldToken);
    }

    @Test
    void refreshToken_whenUserIdentityDoesNotExist_throwsUnauthorized() {
        User user = verifiedUser();
        Token oldToken = Token.builder().user(user).hash("old-hash").build();

        when(tokenService.findValidUnusedToken("old-refresh-token", TokenType.REFRESH_TOKEN,
                "Invalid refresh token"))
                .thenReturn(oldToken);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.refreshToken("old-refresh-token"));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(tokenService, never()).markTokenUsed(any());
        verify(jwtService, never()).buildAccessToken(any(), anyString());
    }

    @Test
    void loginWithGoogle_whenIdentityExists_returnsTokensAndCreatesSession() {
        GoogleOauthRequest request = new GoogleOauthRequest(
                "google-token",
                new DeviceInfo(DeviceType.DESKTOP, "fp-google", "Chrome"));
        User user = User.builder().id(11L).username("google-user").tier(AccountTier.FREE).build();
        AuthIdentity identity = AuthIdentity.builder()
                .user(user)
                .email("google@example.com")
                .providerUserId("google-subject")
                .emailVerified(true)
                .provider(AuthProvider.GOOGLE)
                .type(AuthType.OAUTH)
                .build();
        Token refreshToken = Token.builder().hash("hash").build();

        when(googleTokenVerificationService.verifyAuthCode("google-token"))
                .thenReturn(new VerifiedGoogleToken(
                        "google-subject", "google@example.com", true, "Google User",
                        "avatar.png"));
        when(authIdentityRepository.findByProviderUserIdAndProviderAndType(
                "google-subject", AuthProvider.GOOGLE, AuthType.OAUTH))
                .thenReturn(Optional.of(identity));
        when(jwtService.buildAccessToken(user, identity.getEmail())).thenReturn("google-access-token");
        when(tokenService.createRefreshToken(user))
                .thenReturn(new TokenService.IssuedToken("google-refresh-token", refreshToken));

        AuthService.AuthLoginResult result = authService.loginWithGoogle(request);

        assertEquals("google-access-token", result.response().accessToken());
        assertEquals("google-refresh-token", result.refreshToken());
        verify(sessionService).createSession(user, refreshToken, request.deviceInfo());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithGoogle_whenIdentityIsNew_registersUserAndIdentity() {
        GoogleOauthRequest request = new GoogleOauthRequest(
                "google-token",
                new DeviceInfo(DeviceType.MOBILE, "fp-google-new", "Pixel"));
        Token refreshToken = Token.builder().hash("hash").build();
        User savedUser = User.builder().id(15L).username("googleuser_345678").tier(AccountTier.FREE)
                .build();

        when(googleTokenVerificationService.verifyAuthCode("google-token"))
                .thenReturn(new VerifiedGoogleToken(
                        "123456789012345678", "new-google@example.com", true, "Google User",
                        "avatar.png"));
        when(authIdentityRepository.findByProviderUserIdAndProviderAndType(
                "123456789012345678", AuthProvider.GOOGLE, AuthType.OAUTH))
                .thenReturn(Optional.empty());
        when(authIdentityRepository.existsByEmailIgnoreCase("new-google@example.com")).thenReturn(false);
        when(userRepository.findByUsername("googleuser_345678")).thenReturn(Optional.empty());
        when(userRepository.save(any(User.class))).thenReturn(savedUser);
        when(authIdentityRepository.save(any(AuthIdentity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
        when(jwtService.buildAccessToken(savedUser, "new-google@example.com"))
                .thenReturn("google-access-token");
        when(tokenService.createRefreshToken(savedUser))
                .thenReturn(new TokenService.IssuedToken("google-refresh-token", refreshToken));

        AuthService.AuthLoginResult result = authService.loginWithGoogle(request);

        assertEquals("google-access-token", result.response().accessToken());
        assertEquals("/users/" + savedUser.getUsername(), result.response().user().profileUrl());
        verify(userRepository).save(any(User.class));
        verify(authIdentityRepository).save(any(AuthIdentity.class));
        verify(sessionService).createSession(savedUser, refreshToken, request.deviceInfo());
    }

    @Test
    void loginWithGoogle_whenEmailAlreadyExists_throwsConflict() {
        GoogleOauthRequest request = new GoogleOauthRequest(
                "google-token",
                new DeviceInfo(DeviceType.DESKTOP, "fp-google", "Chrome"));

        when(googleTokenVerificationService.verifyAuthCode("google-token"))
                .thenReturn(new VerifiedGoogleToken(
                        "google-subject", "existing@example.com", true, "Google User",
                        "avatar.png"));
        when(authIdentityRepository.findByProviderUserIdAndProviderAndType(
                "google-subject", AuthProvider.GOOGLE, AuthType.OAUTH))
                .thenReturn(Optional.empty());
        when(authIdentityRepository.existsByEmailIgnoreCase("existing@example.com")).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.loginWithGoogle(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void loginWithGoogle_whenTokenIsInvalid_propagatesUnauthorizedError() {
        GoogleOauthRequest request = new GoogleOauthRequest(
                "bad-google-token",
                new DeviceInfo(DeviceType.DESKTOP, "fp-google", "Chrome"));

        when(googleTokenVerificationService.verifyAuthCode("bad-google-token"))
                .thenThrow(new InvalidGoogleTokenException("Invalid Google auth code."));

        InvalidGoogleTokenException exception = assertThrows(InvalidGoogleTokenException.class,
                () -> authService.loginWithGoogle(request));

        assertEquals("Invalid Google auth code.", exception.getMessage());
        verifyNoInteractions(userRepository, authIdentityRepository, tokenService, sessionService);
    }

    // Helper methods
    private RegisterLocalRequest registerRequest() {
        return new RegisterLocalRequest("new@example.com", "new-user", "Password123",
                LocalDate.of(2000, 1, 1), "MALE", "Cairo", "Egypt", "captcha",
                new DeviceInfo(DeviceType.DESKTOP, "fp", "Dell"));
    }

    private LoginLocalRequest loginRequest() {
        return new LoginLocalRequest("verified@example.com", "Password123",
                new DeviceInfo(DeviceType.MOBILE, "fp", "iPhone"));
    }

    private User verifiedUser() {
        return User.builder().username("user").tier(AccountTier.PRO).build();
    }

    private AuthIdentity verifiedIdentity(User user) {
        return AuthIdentity.builder().user(user).email("verified@example.com").passwordHash("hash")
                .emailVerified(true).provider(AuthProvider.LOCAL).type(AuthType.PASSWORD).build();
    }
}
