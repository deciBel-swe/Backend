package software.decibel.services;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;
import software.decibel.repositories.UserRepository;

/**
 * Service class responsible for handling account recovery logic, including
 * generating and validating password reset tokens, and updating user passwords.
 */
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private static final String INVALID_OR_EXPIRED_TOKEN_MESSAGE = "Invalid or expired token";

    private final UserRepository userRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final FrontendLinkService frontendLinkService;
    private final PasswordEncoder passwordEncoder;

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
            return; // to prevent email enumeration
        }

        User user = optionalUser.get();
        tokenService.deleteTokensForUserAndType(user, TokenType.PASSWORD_RESET);
        tokenService.deleteExpiredTokens();
        TokenService.IssuedToken issuedToken = tokenService.createPasswordResetToken(user);

        String resetLink = frontendLinkService.buildPasswordResetLink(issuedToken.rawToken());
        emailService.sendPasswordResetEmail(user.getEmail(), resetLink);
    }

    // Resets the user's password using a valid raw reset token.
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Token token = findValidPasswordResetToken(rawToken);
        User user = token.getUser();

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));

        userRepository.save(user);
        tokenService.deleteToken(token);
    }

    /**
     * Finds and validates a password reset token by its raw value.
     *
     * @param rawToken the raw token to find and validate
     * @return the valid Token entity
     * @throws ResponseStatusException if the token is invalid or expired
     */
    private Token findValidPasswordResetToken(String rawToken) {
        return tokenService.findValidUnusedToken(rawToken, TokenType.PASSWORD_RESET, INVALID_OR_EXPIRED_TOKEN_MESSAGE);
    }
}
