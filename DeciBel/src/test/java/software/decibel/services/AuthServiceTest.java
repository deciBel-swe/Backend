package software.decibel.services;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.dtos.auth.DeviceInfo;
import software.decibel.dtos.auth.LoginLocalRequest;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.auth.RegisterLocalRequest;
import software.decibel.dtos.auth.VerifyEmailRequest;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.DeviceType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.UserRepository;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
    private EmailService emailService;

    @Mock
    private FrontendLinkService frontendLinkService;

    @InjectMocks
    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService.initSigningKey();
    }

    @Test
    void registerLocal_whenRequestIsValid_savesUserIdentityAndVerificationToken() {
        RegisterLocalRequest request = registerRequest();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.empty());
        when(authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(
                request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)).thenReturn(false);
        when(userRepository.findByUsername(request.username())).thenReturn(Optional.empty());
        when(passwordEncoder.encode(request.password())).thenReturn("hashed-password");
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User user = invocation.getArgument(0);
            user.setId(7L);
            return user;
        });
        Token verificationToken = Token.builder()
                .user(User.builder().id(7L).build())
                .tokenType(TokenType.EMAIL_VERIFICATION)
                .hash("verification-token-hash")
                .expiresAt(LocalDateTime.of(2026, 3, 13, 12, 30))
                .build();
        when(tokenService.createEmailVerificationToken(any(User.class)))
                .thenReturn(new TokenService.IssuedToken("raw-verification-token", verificationToken));
        when(frontendLinkService.buildEmailVerificationLink("raw-verification-token"))
                .thenReturn("https://decibel.foo/verify-email?token=raw-verification-token");

        MessageResponse response = authService.registerLocal(request);

        assertEquals("User Generated successfully", response.message());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());
        User savedUser = userCaptor.getValue();
        assertEquals("new@example.com", savedUser.getEmail());
        assertEquals("new-user", savedUser.getUsername());
        assertEquals("hashed-password", savedUser.getPasswordHash());
        assertFalse(savedUser.isEmailVerified());
        assertEquals("Cairo, Egypt", savedUser.getLocation());

        ArgumentCaptor<AuthIdentity> identityCaptor = ArgumentCaptor.forClass(AuthIdentity.class);
        verify(authIdentityRepository).save(identityCaptor.capture());
        AuthIdentity savedIdentity = identityCaptor.getValue();
        assertEquals("new@example.com", savedIdentity.getEmail());
        assertEquals("hashed-password", savedIdentity.getPasswordHash());
        assertFalse(savedIdentity.isEmailVerified());
        assertEquals(AuthProvider.LOCAL, savedIdentity.getProvider());
        assertEquals(AuthType.PASSWORD, savedIdentity.getType());
        assertNotNull(savedIdentity.getUser());
        assertEquals(7L, savedIdentity.getUser().getId());

        verify(tokenService).createEmailVerificationToken(any(User.class));
        verify(emailService).sendEmailVerificationEmail(
                "new@example.com",
                "https://decibel.foo/verify-email?token=raw-verification-token"
        );
    }

    @Test
    void registerLocal_whenEmailAlreadyExists_throwsConflict() {
        RegisterLocalRequest request = registerRequest();
        when(userRepository.findByEmail(request.email())).thenReturn(Optional.of(User.builder().id(1L).build()));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.registerLocal(request));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
        verify(userRepository).findByEmail(request.email());
        verify(authIdentityRepository, never()).save(any(AuthIdentity.class));
        verify(tokenService, never()).createEmailVerificationToken(any(User.class));
    }

    @Test
    void loginLocal_whenCredentialsAreValid_returnsAccessAndRefreshTokens() {
        LoginLocalRequest request = loginRequest();
        User user = verifiedUser();
        user.setId(5L);
        AuthIdentity identity = verifiedIdentity(user);

        when(authIdentityRepository.findByEmailIgnoreCaseAndProviderAndType(
                request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)).thenReturn(Optional.of(identity));
        when(passwordEncoder.matches(request.password(), identity.getPasswordHash())).thenReturn(true);
        Token refreshToken = Token.builder()
                .user(user)
                .tokenType(TokenType.REFRESH_TOKEN)
                .hash("refresh-token-hash")
                .expiresAt(LocalDateTime.of(2026, 4, 12, 10, 0))
                .build();
        when(tokenService.createRefreshToken(user))
                .thenReturn(new TokenService.IssuedToken("refresh-token", refreshToken));

        AuthService.AuthLoginResult result = authService.loginLocal(request);

        assertNotNull(result.response().accessToken());
        assertEquals(1800L, result.response().expiresIn());
        assertEquals("refresh-token", result.refreshToken());
        assertEquals(2592000L, result.refreshTokenExpiresIn());
        assertEquals(5L, result.response().user().id());
        assertEquals("verified-user", result.response().user().username());
        assertEquals(AccountTier.ARTIST, result.response().user().tier());

        verify(tokenService).createRefreshToken(user);
    }

    @Test
    void loginLocal_whenPasswordIsInvalid_throwsUnauthorized() {
        LoginLocalRequest request = loginRequest();
        User user = verifiedUser();
        AuthIdentity identity = verifiedIdentity(user);

        when(authIdentityRepository.findByEmailIgnoreCaseAndProviderAndType(
                request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)).thenReturn(Optional.of(identity));
        when(passwordEncoder.matches(request.password(), identity.getPasswordHash())).thenReturn(false);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.loginLocal(request));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
        verify(tokenService, never()).createRefreshToken(any(User.class));
    }

    @Test
    void loginLocal_whenEmailIsNotVerified_throwsForbidden() {
        LoginLocalRequest request = loginRequest();
        User user = verifiedUser();
        user.setEmailVerified(false);
        AuthIdentity identity = verifiedIdentity(user);

        when(authIdentityRepository.findByEmailIgnoreCaseAndProviderAndType(
                request.email(), AuthProvider.LOCAL, AuthType.PASSWORD)).thenReturn(Optional.of(identity));
        when(passwordEncoder.matches(request.password(), identity.getPasswordHash())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.loginLocal(request));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatusCode());
        verify(tokenService, never()).createRefreshToken(any(User.class));
    }

    @Test
    void verifyEmail_whenTokenIsValid_marksUserAndIdentityVerifiedAndReturnsRefreshToken() {
        VerifyEmailRequest request = new VerifyEmailRequest("raw-token");
        User user = User.builder()
                .id(9L)
                .email("pending@example.com")
                .username("pending-user")
                .tier(AccountTier.LISTENER)
                .isEmailVerified(false)
                .build();
        AuthIdentity identity = AuthIdentity.builder()
                .authId(4L)
                .user(user)
                .email(user.getEmail())
                .passwordHash("hashed-password")
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .emailVerified(false)
                .build();
        Token verificationToken = Token.builder()
                .tokenId(15L)
                .user(user)
                .tokenType(TokenType.EMAIL_VERIFICATION)
                .hash("hashed-verify-token")
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();

        when(tokenService.findValidUnusedToken("raw-token", TokenType.EMAIL_VERIFICATION, "Invalid verification token"))
                .thenReturn(verificationToken);
        when(authIdentityRepository.findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD))
                .thenReturn(Optional.of(identity));
        Token refreshToken = Token.builder()
                .user(user)
                .tokenType(TokenType.REFRESH_TOKEN)
                .hash("refresh-token-hash")
                .expiresAt(LocalDateTime.of(2026, 4, 12, 10, 0))
                .build();
        when(tokenService.createRefreshToken(user))
                .thenReturn(new TokenService.IssuedToken("refresh-token", refreshToken));

        AuthService.AuthRefreshTokenResult result = authService.verifyEmail(request);

        assertEquals("refresh-token", result.refreshToken());
        assertEquals(2592000L, result.refreshTokenExpiresIn());
        assertTrue(user.isEmailVerified());
        assertTrue(identity.isEmailVerified());

        verify(userRepository).save(user);
        verify(authIdentityRepository).save(identity);
        verify(tokenService).markTokenUsed(verificationToken);
        verify(tokenService).createRefreshToken(user);
    }

    @Test
    void verifyEmail_whenTokenDoesNotExist_throwsBadRequest() {
        when(tokenService.findValidUnusedToken("missing-token", TokenType.EMAIL_VERIFICATION, "Invalid verification token"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.verifyEmail(new VerifyEmailRequest("missing-token")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(authIdentityRepository, never()).save(any(AuthIdentity.class));
    }

    @Test
    void verifyEmail_whenTokenIsExpired_throwsBadRequest() {
        when(tokenService.findValidUnusedToken("expired-token", TokenType.EMAIL_VERIFICATION, "Invalid verification token"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid verification token"));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> authService.verifyEmail(new VerifyEmailRequest("expired-token")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(userRepository, never()).save(any(User.class));
        verify(authIdentityRepository, never()).save(any(AuthIdentity.class));
    }

    private RegisterLocalRequest registerRequest() {
        return new RegisterLocalRequest(
                "new@example.com",
                "new-user",
                "Password123",
                LocalDate.of(2000, 1, 1),
                "MALE",
                "Cairo",
                "Egypt",
                "captcha-token",
                new DeviceInfo(DeviceType.DESKTOP, "fp-123", "Dell Laptop"));
    }

    private LoginLocalRequest loginRequest() {
        return new LoginLocalRequest(
                "verified@example.com",
                "Password123",
                new DeviceInfo(DeviceType.MOBILE, "fp-999", "iPhone"));
    }

    private User verifiedUser() {
        return User.builder()
                .email("verified@example.com")
                .username("verified-user")
                .tier(AccountTier.ARTIST)
                .avatarUrl("avatar.png")
                .isEmailVerified(true)
                .build();
    }

    private AuthIdentity verifiedIdentity(User user) {
        return AuthIdentity.builder()
                .user(user)
                .email(user.getEmail())
                .passwordHash("hashed-password")
                .emailVerified(true)
                .provider(AuthProvider.LOCAL)
                .type(AuthType.PASSWORD)
                .build();
    }
}
