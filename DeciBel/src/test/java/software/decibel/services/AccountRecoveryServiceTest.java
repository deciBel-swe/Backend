package software.decibel.services;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;
import software.decibel.repositories.TokenRepository;
import software.decibel.repositories.UserRepository;

@ExtendWith(MockitoExtension.class)
class AccountRecoveryServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private EmailService emailService;

    @Mock
    private PasswordEncoder passwordEncoder;

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

        ReflectionTestUtils.setField(accountRecoveryService, "frontendBaseUrl", "https://decibel.foo");
    }

    @Test
    void forgotPassword_shouldDoNothing_whenUserDoesNotExist() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertDoesNotThrow(() -> accountRecoveryService.forgotPassword("missing@example.com"));

        verify(userRepository).findByEmail("missing@example.com");
        verifyNoInteractions(tokenRepository, emailService, passwordEncoder);
    }

    @Test
    void forgotPassword_shouldCreateAndSaveResetToken_whenUserExists() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        accountRecoveryService.forgotPassword("test@example.com");

        verify(userRepository).findByEmail("test@example.com");
        verify(tokenRepository).deleteByUserAndTokenType(user, TokenType.PASSWORD_RESET);
        verify(tokenRepository).deleteByExpiresAtBefore(any(LocalDateTime.class));

        ArgumentCaptor<Token> tokenCaptor = ArgumentCaptor.forClass(Token.class);
        verify(tokenRepository).save(tokenCaptor.capture());

        Token savedToken = tokenCaptor.getValue();

        assertNotNull(savedToken);
        assertEquals(TokenType.PASSWORD_RESET, savedToken.getTokenType());
        assertEquals(user, savedToken.getUser());
        assertNotNull(savedToken.getHash());
        assertFalse(savedToken.getHash().isBlank());
        assertNotNull(savedToken.getExpiresAt());
        assertTrue(savedToken.getExpiresAt().isAfter(LocalDateTime.now()));

        verify(emailService).sendPasswordResetEmail(
                eq("test@example.com"),
                contains("https://decibel.foo/reset-password?token=")
        );
        verifyNoInteractions(passwordEncoder);
    }

    @Test
    void resetPassword_shouldUpdatePasswordAndDeleteToken_whenTokenIsValid() {
        Token token = new Token();
        token.setTokenType(TokenType.PASSWORD_RESET);
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().plusMinutes(30));

        when(tokenRepository.findByHashAndTokenType(any(String.class), eq(TokenType.PASSWORD_RESET)))
                .thenReturn(Optional.of(token));
        when(passwordEncoder.encode("NewPassword1!")).thenReturn("encoded-password");

        accountRecoveryService.resetPassword("raw-reset-token", "NewPassword1!");

        assertEquals("encoded-password", user.getPasswordHash());
        assertNotNull(token.getUsedAt());

        verify(passwordEncoder).encode("NewPassword1!");
        verify(userRepository).save(user);
        verify(tokenRepository).delete(token);
    }

    @Test
    void resetPassword_shouldThrowBadRequest_whenTokenIsExpired() {
        Token token = new Token();
        token.setTokenType(TokenType.PASSWORD_RESET);
        token.setUser(user);
        token.setExpiresAt(LocalDateTime.now().minusMinutes(1));

        when(tokenRepository.findByHashAndTokenType(any(String.class), eq(TokenType.PASSWORD_RESET)))
                .thenReturn(Optional.of(token));

        ResponseStatusException ex = assertThrows(
                ResponseStatusException.class,
                () -> accountRecoveryService.resetPassword("raw-reset-token", "NewPassword1!")
        );

        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
        verify(tokenRepository).delete(token);
    }
}
