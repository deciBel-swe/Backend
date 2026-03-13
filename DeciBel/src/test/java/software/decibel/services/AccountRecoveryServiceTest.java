package software.decibel.services;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;
import software.decibel.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TokenService tokenService;

    @Mock
    private FrontendLinkService frontendLinkService;

    @InjectMocks
    private AccountRecoveryService accountRecoveryService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("test@example.com")
                .username("tester")
                .build();
    }

    @Test
    void forgotPassword_shouldDoNothing_whenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> accountRecoveryService.forgotPassword("missing@example.com"));

        verify(userRepository).findByEmail("missing@example.com");
        verifyNoInteractions(tokenService, emailService, passwordEncoder);
    }

    @Test
    void forgotPassword_shouldCreateAndSaveResetToken_whenUserExists() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        Token token = new Token();
        token.setTokenType(TokenType.PASSWORD_RESET);
        token.setUser(user);
        token.setHash("hashed-token");
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));
        when(tokenService.createPasswordResetToken(user))
                .thenReturn(new TokenService.IssuedToken("raw-reset-token", token));
        when(frontendLinkService.buildPasswordResetLink("raw-reset-token"))
                .thenReturn("https://decibel.foo/reset-password?token=raw-reset-token");

        accountRecoveryService.forgotPassword("test@example.com");

        verify(userRepository).findByEmail("test@example.com");
        verify(tokenService).deleteTokensForUserAndType(user, TokenType.PASSWORD_RESET);
        verify(tokenService).deleteExpiredTokens();
        verify(tokenService).createPasswordResetToken(user);

        verify(emailService).sendPasswordResetEmail(
                eq("test@example.com"),
                eq("https://decibel.foo/reset-password?token=raw-reset-token")
        );
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void resetPassword_shouldUpdatePasswordAndDeleteToken_whenTokenIsValid() {
        Token token = new Token();
        token.setTokenType(TokenType.PASSWORD_RESET);
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        when(tokenService.findValidUnusedToken("raw-reset-token", TokenType.PASSWORD_RESET, "Invalid or expired token"))
                .thenReturn(token);
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-password");

        accountRecoveryService.resetPassword("raw-reset-token", "NewPassword1!");

        assertEquals("encoded-password", user.getPasswordHash());

        verify(passwordEncoder).encode("NewPassword1!");
        verify(userRepository).save(user);
        verify(tokenService).deleteToken(token);
    }

    @Test
    void resetPassword_shouldThrowBadRequest_whenTokenIsExpired() {
        when(tokenService.findValidUnusedToken("raw-reset-token", TokenType.PASSWORD_RESET, "Invalid or expired token"))
                .thenThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid or expired token"));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accountRecoveryService.resetPassword("raw-reset-token", "NewPassword1!")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
    }
}
