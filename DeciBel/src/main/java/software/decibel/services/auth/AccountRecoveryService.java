package software.decibel.services.auth;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.IssuedToken;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.services.EmailService;
import software.decibel.services.FrontendLinkService;
import software.decibel.services.TokenService;

/**
 * Service class responsible for handling account recovery logic, including
 * generating and validating password reset tokens, and updating user passwords.
 */
@Service
@RequiredArgsConstructor
public class AccountRecoveryService {

    private static final String INVALID_OR_EXPIRED_TOKEN_MESSAGE = "Invalid or expired token";

    private final AuthIdentityRepository authIdentityRepository;
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
        Optional<AuthIdentity> optionalIdentity = authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(email, AuthProvider.LOCAL, AuthType.PASSWORD);

        if (optionalIdentity.isEmpty()) {
            return; // to prevent email enumeration
        }

        AuthIdentity identity = optionalIdentity.get();
        User user = identity.getUser();
        tokenService.deleteTokensForUserAndType(user, TokenType.PASSWORD_RESET);
        tokenService.deleteExpiredTokens();
        IssuedToken issuedToken = tokenService.createPasswordResetToken(user);

        String resetLink = frontendLinkService.buildPasswordResetLink(issuedToken.rawToken());
        emailService.sendPasswordResetEmail(identity.getEmail(), resetLink);
    }

    // Resets the user's password using a valid raw reset token.
    @Transactional
    public void resetPassword(String rawToken, String newPassword) {
        Token token = findValidPasswordResetToken(rawToken);
        User user = token.getUser();

        if (user == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_OR_EXPIRED_TOKEN_MESSAGE);
        }

        AuthIdentity identity = authIdentityRepository
                .findByUserAndProviderAndType(user, AuthProvider.LOCAL, AuthType.PASSWORD)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_OR_EXPIRED_TOKEN_MESSAGE));

        identity.setPasswordHash(passwordEncoder.encode(newPassword));
        authIdentityRepository.save(identity);
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
