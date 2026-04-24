package software.decibel.services.user;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.auth.MessageResponse;
import software.decibel.dtos.user.ChangePasswordRequest;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TokenType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.services.TokenService;
@Service
@RequiredArgsConstructor
public class UserPasswordService {

    private final UserService userService;
    private final AuthIdentityRepository authIdentityRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    @Transactional
    public MessageResponse resetMyPassword(Authentication authentication, ChangePasswordRequest request) {
        User currentUser = resolveCurrentUser(authentication);

        AuthIdentity authIdentity = authIdentityRepository.findByUserAndProviderAndType(
                currentUser,
                AuthProvider.LOCAL,
                AuthType.PASSWORD
        ).orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No local auth identity found"));

        if (!passwordEncoder.matches(request.currentPassword(), authIdentity.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Current password is incorrect");
        }

        if (passwordEncoder.matches(request.newPassword(), authIdentity.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "New password must be different from current password");
        }

        authIdentity.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        authIdentityRepository.save(authIdentity);
        tokenService.deleteTokensForUserAndType(currentUser, TokenType.REFRESH_TOKEN);

        return new MessageResponse("Password changed successfully");
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String principal = authentication.getName();
        if (principal == null || principal.isBlank() || "anonymousUser".equalsIgnoreCase(principal)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        try {
            return userService.getUserIfExistsByUsername(principal);
        } catch (software.decibel.exceptions.custom.ResourceNotFoundException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found");
        }
    }
}
