package software.decibel.services.user;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.IssuedToken;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangeEmailRequest;
import software.decibel.dtos.user.VerifyEmailChangeRequest;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.PendingEmailChange;
import software.decibel.entities.Token;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.PendingEmailChangeRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.EmailService;
import software.decibel.services.FrontendLinkService;
import software.decibel.services.TokenService;

@Service
@RequiredArgsConstructor
public class UserEmailService {

    private static final String INVALID_EMAIL_CHANGE_TOKEN_MESSAGE = "Invalid or expired token";

    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final PendingEmailChangeRepository pendingEmailChangeRepository;
    private final TokenService tokenService;
    private final EmailService emailService;
    private final FrontendLinkService frontendLinkService;

    @Transactional
    public MessageResponse requestMyEmailChange(Authentication authentication, ChangeEmailRequest request) {
        User currentUser = resolveCurrentUser(authentication);
        AuthIdentity currentIdentity = authIdentityRepository.findByUserAndProviderAndType(
                currentUser,
                AuthProvider.LOCAL,
                AuthType.PASSWORD
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No local auth identity found"));
        String newEmail = request.newEmail().trim();

        if (currentIdentity.getEmail().equalsIgnoreCase(newEmail)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New email must be different from current email");
        }

        if (authIdentityRepository.existsByEmailIgnoreCase(newEmail)
                || pendingEmailChangeRepository.existsByNewEmailIgnoreCase(newEmail)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        pendingEmailChangeRepository.findByUser(currentUser).ifPresent(existingRequest -> {
            pendingEmailChangeRepository.delete(existingRequest);
            tokenService.deleteToken(existingRequest.getToken());
        });
        tokenService.deleteTokensForUserAndType(currentUser, TokenType.EMAIL_CHANGE);

        IssuedToken issuedToken = tokenService.createEmailChangeToken(currentUser);
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
        boolean emailExists = authIdentityRepository.existsByEmailIgnoreCase(newEmail);
        boolean currentUserAlreadyOwnsEmail = authIdentityRepository.findAllByUser(currentUser).stream()
                .anyMatch(identity -> identity.getEmail().equalsIgnoreCase(newEmail));
        if (emailExists && !currentUserAlreadyOwnsEmail) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email already exists");
        }

        List<AuthIdentity> identities = authIdentityRepository.findAllByUser(currentUser);
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
