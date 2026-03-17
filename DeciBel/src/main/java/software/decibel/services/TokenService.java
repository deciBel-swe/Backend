package software.decibel.services;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;
import software.decibel.repositories.TokenRepository;
import software.decibel.utils.TokenUtility;

import java.time.LocalDateTime;

@Service
public class TokenService {

    private static final int DEFAULT_TOKEN_BYTES = 32;
    private static final int REFRESH_TOKEN_BYTES = 48;
    private static final long EMAIL_VERIFICATION_EXPIRATION_MINUTES = 30L;
    private static final long EMAIL_CHANGE_EXPIRATION_MINUTES = 30L;
    private static final long PASSWORD_RESET_EXPIRATION_MINUTES = 30L;
    private static final long REFRESH_TOKEN_EXPIRATION_MINUTES = 60L * 24L * 30L;

    private final TokenRepository tokenRepository;
    private final TokenUtility tokenUtility;

    public TokenService(TokenRepository tokenRepository, TokenUtility tokenUtility) {
        this.tokenRepository = tokenRepository;
        this.tokenUtility = tokenUtility;
    }

    @Transactional
    public IssuedToken createEmailVerificationToken(User user) {
        return issueToken(user, TokenType.EMAIL_VERIFICATION, DEFAULT_TOKEN_BYTES, EMAIL_VERIFICATION_EXPIRATION_MINUTES);
    }

    @Transactional
    public IssuedToken createRefreshToken(User user) {
        return issueToken(user, TokenType.REFRESH_TOKEN, REFRESH_TOKEN_BYTES, REFRESH_TOKEN_EXPIRATION_MINUTES);
    }

    @Transactional
    public IssuedToken createEmailChangeToken(User user) {
        return issueToken(user, TokenType.EMAIL_CHANGE, DEFAULT_TOKEN_BYTES, EMAIL_CHANGE_EXPIRATION_MINUTES);
    }

    @Transactional
    public IssuedToken createPasswordResetToken(User user) {
        return issueToken(user, TokenType.PASSWORD_RESET, DEFAULT_TOKEN_BYTES, PASSWORD_RESET_EXPIRATION_MINUTES);
    }

    @Transactional(readOnly = true)
    public Token findValidUnusedToken(String rawToken, TokenType tokenType, String invalidMessage) {
        String tokenHash = tokenUtility.hashToken(rawToken);
        Token token = tokenRepository.findByHashAndTokenTypeAndUsedAtIsNull(tokenHash, tokenType)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidMessage));

        if (tokenUtility.isExpired(token.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, invalidMessage);
        }

        return token;
    }

    @Transactional
    public void markTokenUsed(Token token) {
        token.setUsedAt(LocalDateTime.now());
        tokenRepository.save(token);
    }

    @Transactional
    public void deleteToken(Token token) {
        tokenRepository.delete(token);
    }

    @Transactional
    public void deleteExpiredTokens() {
        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());
    }

    @Transactional
    public void deleteTokensForUserAndType(User user, TokenType tokenType) {
        tokenRepository.deleteByUserAndTokenType(user, tokenType);
    }

    private IssuedToken issueToken(User user, TokenType tokenType, int bytesLength, long expirationMinutes) {
        String rawToken = tokenUtility.generateToken(bytesLength);
        Token token = Token.builder()
                .user(user)
                .tokenType(tokenType)
                .hash(tokenUtility.hashToken(rawToken))
                .expiresAt(tokenUtility.expiresInMinutes(expirationMinutes))
                .build();
        tokenRepository.save(token);
        return new IssuedToken(rawToken, token);
    }

    public record IssuedToken(String rawToken, Token token) {
    }
}
