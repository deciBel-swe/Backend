package software.decibel.services;

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

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private AuthIdentityRepository authIdentityRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private TokenService tokenService;
    @Mock private SessionService sessionService;
    @Mock private EmailService emailService;
    @Mock private FrontendLinkService frontendLinkService;
    @Mock private JwtService jwtService;

    @InjectMocks
    private AuthService authService;

    @Test
    void registerLocal_whenRequestIsValid_savesUserAndSendsEmail() {
        RegisterLocalRequest request = registerRequest();
        when(authIdentityRepository.existsByEmailIgnoreCase(request.email())).thenReturn(false);
        when(authIdentityRepository.existsByEmailIgnoreCaseAndProviderAndType(anyString(), any(), any())).thenReturn(false);
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
        assertEquals(AccountTier.ARTIST, result.response().user().tier()); // Role verified!
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
        return User.builder().username("user").tier(AccountTier.ARTIST).build();
    }

    private AuthIdentity verifiedIdentity(User user) {
        return AuthIdentity.builder().user(user).email("verified@example.com").passwordHash("hash").emailVerified(true).provider(AuthProvider.LOCAL).type(AuthType.PASSWORD).build();
    }
}
