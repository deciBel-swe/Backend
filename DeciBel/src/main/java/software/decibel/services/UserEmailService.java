package software.decibel.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangeEmailRequest;
import software.decibel.dtos.user.VerifyEmailChangeRequest;
import software.decibel.entities.PendingEmailChange;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.PendingEmailChangeRepository;
import software.decibel.repositories.UserRepository;

import java.util.List;

@Service
public class UserEmailService {

    private static final String INVALID_EMAIL_CHANGE_TOKEN_MESSAGE = "Invalid or expired token";

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PendingEmailChangeRepository pendingEmailChangeRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final FrontendLinkService frontendLinkService;

    public UserEmailService(
            UserRepository userRepository,
            AuthIdentityRepository authIdentityRepository,
            PendingEmailChangeRepository pendingEmailChangeRepository,
            TokenService tokenService,
            EmailService emailService,
            FrontendLinkService frontendLinkService) {
        this.userRepository = userRepository;
        this.authIdentityRepository = authIdentityRepository;
        this.pendingEmailChangeRepository = pendingEmailChangeRepository;
        this.tokenService = tokenService;
        this.emailService = emailService;
        this.frontendLinkService = frontendLinkService;
    }

    @Transactional
    public MessageResponse requestMyEmailChange(Authentication authentication, ChangeEmailRequest request) {
        User currentUser = resolveCurrentUser(authentication);
        String newEmail = request.newEmail().trim();

        if (currentUser.getEmail().equalsIgnoreCase(newEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email must be different from current email");
        }

        if (userRepository.existsByEmailIgnoreCase(newEmail)
                || pendingEmailChangeRepository.existsByNewEmailIgnoreCase(newEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        pendingEmailChangeRepository.findByUser(currentUser).ifPresent(existingRequest -> {
            pendingEmailChangeRepository.delete(existingRequest);
            tokenService.deleteToken(existingRequest.getToken());
        });
        tokenService.deleteTokensForUserAndType(currentUser, TokenType.EMAIL_CHANGE);

        TokenService.IssuedToken issuedToken = tokenService.createEmailChangeToken(currentUser);
        PendingEmailChange pendingEmailChange = PendingEmailChange.builder()
                .user(currentUser)
                .newEmail(newEmail)
                .token(issuedToken.token())
                .build();
        pendingEmailChangeRepository.save(pendingEmailChange);

        // Current flow verifies ownership of the new email address only.
        // It does not require a separate approval step from the old email address.
        String verificationLink = frontendLinkService.buildEmailChangeVerificationLink(issuedToken.rawToken());
        emailService.sendEmailChangeVerificationEmail(newEmail, verificationLink);
        return new MessageResponse("Verification email sent successfully");
    }

    /*
     * Verifies the user's email change request using a valid raw verification
     * token.
     * If valid, updates the user's email and marks it as verified.
     */
    @Transactional
    public MessageResponse verifyMyEmailChange(Authentication authentication, VerifyEmailChangeRequest request) {
        User currentUser = resolveCurrentUser(authentication);
        Token emailChangeToken = tokenService.findValidUnusedToken(
                request.token(),
                TokenType.EMAIL_CHANGE,
                INVALID_EMAIL_CHANGE_TOKEN_MESSAGE);

        PendingEmailChange pendingEmailChange = pendingEmailChangeRepository.findByToken(emailChangeToken)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_EMAIL_CHANGE_TOKEN_MESSAGE));

        if (!pendingEmailChange.getUser().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Forbidden");
        }

        String newEmail = pendingEmailChange.getNewEmail();
        if (userRepository.existsByEmailIgnoreCase(newEmail) && !currentUser.getEmail().equalsIgnoreCase(newEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        currentUser.setEmail(newEmail);
        currentUser.setEmailVerified(true);
        userRepository.save(currentUser);

        List<software.decibel.entities.AuthIdentity> identities = authIdentityRepository.findAllByUser(currentUser);
        identities.forEach(identity -> {
            identity.setEmail(newEmail);
            identity.setEmailVerified(true);
        });
        authIdentityRepository.saveAll(identities);

        pendingEmailChangeRepository.delete(pendingEmailChange);
        tokenService.markTokenUsed(emailChangeToken);

        return new MessageResponse("Email changed successfully");
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String principal = authentication.getName();
        if (principal == null || principal.isBlank() || "anonymousUser".equalsIgnoreCase(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        final long userId;
        try {
            userId = Long.parseLong(principal);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid ID format");
        }

        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
