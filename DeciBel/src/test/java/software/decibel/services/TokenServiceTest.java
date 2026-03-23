package software.decibel.services;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.auth.IssuedToken;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;
import software.decibel.repositories.TokenRepository;
import software.decibel.utils.TokenUtility;

@ExtendWith(MockitoExtension.class)
class TokenServiceTest {

    @Mock
    private TokenRepository tokenRepository;

    @Mock
    private TokenUtility tokenUtility;

    @InjectMocks
    private TokenService tokenService;

    @Test
    void createEmailVerificationToken_savesTokenAndReturnsRawValue() {
        User user = User.builder().id(1L).username("user").build();
        when(tokenUtility.generateToken(32)).thenReturn("raw-token");
        when(tokenUtility.hashToken("raw-token")).thenReturn("hashed-token");
        when(tokenUtility.expiresInMinutes(30L)).thenReturn(LocalDateTime.of(2026, 3, 13, 13, 0));

        IssuedToken issuedToken = tokenService.createEmailVerificationToken(user);

        assertEquals("raw-token", issuedToken.rawToken());
        assertNotNull(issuedToken.token());

        ArgumentCaptor<Token> tokenCaptor = ArgumentCaptor.forClass(Token.class);
        verify(tokenRepository).save(tokenCaptor.capture());
        Token savedToken = tokenCaptor.getValue();
        assertEquals(user, savedToken.getUser());
        assertEquals(TokenType.EMAIL_VERIFICATION, savedToken.getTokenType());
        assertEquals("hashed-token", savedToken.getHash());
        assertEquals(LocalDateTime.of(2026, 3, 13, 13, 0), savedToken.getExpiresAt());
    }

    @Test
    void findValidUnusedToken_whenTokenExistsAndNotExpired_returnsToken() {
        Token token = Token.builder()
                .hash("hashed-token")
                .tokenType(TokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().plusMinutes(10))
                .build();
        when(tokenUtility.hashToken("raw-token")).thenReturn("hashed-token");
        when(tokenRepository.findByHashAndTokenTypeAndUsedAtIsNull("hashed-token", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(tokenUtility.isExpired(token.getExpiresAt())).thenReturn(false);

        Token resolvedToken = tokenService.findValidUnusedToken(
                "raw-token",
                TokenType.PASSWORD_RESET,
                "Invalid or expired token");

        assertEquals(token, resolvedToken);
    }

    @Test
    void findValidUnusedToken_whenTokenMissing_throwsBadRequest() {
        when(tokenUtility.hashToken("missing-token")).thenReturn("missing-token-hash");
        when(tokenRepository.findByHashAndTokenTypeAndUsedAtIsNull("missing-token-hash", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.empty());

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> tokenService.findValidUnusedToken(
                        "missing-token",
                        TokenType.PASSWORD_RESET,
                        "Invalid or expired token"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    @Test
    void findValidUnusedToken_whenTokenExpired_throwsBadRequest() {
        Token token = Token.builder()
                .hash("hashed-token")
                .tokenType(TokenType.PASSWORD_RESET)
                .expiresAt(LocalDateTime.now().minusMinutes(1))
                .build();
        when(tokenUtility.hashToken("expired-token")).thenReturn("expired-token-hash");
        when(tokenRepository.findByHashAndTokenTypeAndUsedAtIsNull("expired-token-hash", TokenType.PASSWORD_RESET))
                .thenReturn(Optional.of(token));
        when(tokenUtility.isExpired(token.getExpiresAt())).thenReturn(true);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class,
                () -> tokenService.findValidUnusedToken(
                        "expired-token",
                        TokenType.PASSWORD_RESET,
                        "Invalid or expired token"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        verify(tokenRepository, never()).save(any(Token.class));
    }

    @Test
    void markTokenUsed_setsTimestampAndSavesToken() {
        Token token = Token.builder().build();

        tokenService.markTokenUsed(token);

        assertNotNull(token.getUsedAt());
        verify(tokenRepository).save(token);
    }
}
