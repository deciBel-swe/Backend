package software.decibel.services;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;
import software.decibel.repositories.TokenRepository;
import software.decibel.repositories.UserRepository;

/**
 * Service class responsible for handling account recovery logic, including
 * generating and validating password reset tokens, and updating user passwords.
 */
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private static final String INVALID_OR_EXPIRED_TOKEN_MESSAGE = "Invalid or expired token";
    private static final int RESET_TOKEN_EXPIRATION_MINUTES = 30;

    private final UserRepository userRepository;
    private final TokenRepository tokenRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

    /**
     * Initiates the forgot password process for the given email. Generates a
     * secure token, saves its hash, and sends a reset link to the user's email.
     * To prevent email enumeration, it returns silently if the user is not
     * found.
     */
    @Transactional
    public void forgotPassword(String email) {
        Optional<User> optionalUser = userRepository.findByEmail(email);

        if (optionalUser.isEmpty()) {
            return; //to prevent email enumeration
        }

        User user = optionalUser.get();
        tokenRepository.deleteByUserAndTokenType(user, TokenType.PASSWORD_RESET);
        tokenRepository.deleteByExpiresAtBefore(LocalDateTime.now());

        String rawToken = generateRawToken();
        Token token = createPasswordResetToken(user, rawToken);

        tokenRepository.save(token);

        String resetLink = frontendBaseUrl + "/reset-password?token=" + rawToken;
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    //Resets the user's password using a valid raw reset token.
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Token token = findValidPasswordResetToken(rawToken);
        User user = token.getUser();

        if (user == null) {
            tokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        token.setUsedAt(LocalDateTime.now());

        userRepository.save(user);
        tokenRepository.delete(token);
    }

    /**
     * Finds and validates a password reset token by its raw value.
     *
     * @param rawToken the raw token to find and validate
     * @return the valid Token entity
     * @throws ResponseStatusException if the token is invalid or expired
     */
    private Token findValidPasswordResetToken(String rawToken) {
        String tokenHash = hashToken(rawToken);

        Token token = tokenRepository.findByTokenHashAndTokenType(tokenHash, TokenType.PASSWORD_RESET)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        if (token.getExpiresAt() == null || token.getExpiresAt().isBefore(LocalDateTime.now())) {
            tokenRepository.delete(token);
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        return token;
    }

    /**
     * Creates a new password reset token for the specified user.
     *
     * @param user the user for whom the token is created
     * @param rawToken the raw token to be hashed and stored
     * @return a new Token entity
     */
    private Token createPasswordResetToken(User user, String rawToken) {
        Token token = new Token();
        token.setTokenType(TokenType.PASSWORD_RESET);
        token.setHash(hashToken(rawToken));
        token.setExpiresAt(LocalDateTime.now().plusMinutes(RESET_TOKEN_EXPIRATION_MINUTES));
        token.setUser(user);
        return token;
    }

    /**
     * Generates a cryptographically secure random token.
     *
     * @return a URL-safe Base64 encoded raw token
     */
    private String generateRawToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    // Hashes a raw token using SHA-256 and encodes the result in Base64.
    private String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm is not available", e);
        }
    }
}
