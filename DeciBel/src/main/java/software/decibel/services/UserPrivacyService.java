package software.decibel.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.auth.PrivacyUpdateRequest;
import software.decibel.dtos.auth.PrivacyUpdateResponse;
import software.decibel.dtos.auth.UserPrincipal;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;

@Service
public class UserPrivacyService {

    private final UserRepository userRepository;

    public UserPrivacyService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public PrivacyUpdateResponse updateMyPrivacy(Authentication authentication, PrivacyUpdateRequest request) {
        // Resolve the full User entity from the current security context
        User currentUser = resolveCurrentUser(authentication);

        currentUser.setPrivate(request.isPrivate());
        currentUser.setShowHistory(request.showHistory());

        return new PrivacyUpdateResponse(currentUser.isPrivate(), currentUser.isShowHistory());
    }

    private User resolveCurrentUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        // The JwtAuthenticationFilter now sets UserPrincipal as the principal
        Object principal = authentication.getPrincipal();
        final long userId;

        if (principal instanceof UserPrincipal userPrincipal) {
            userId = userPrincipal.getId();
        } else if (principal instanceof User user) {
            // Support legacy behavior if filter hasn't refreshed or for internal mocks
            userId = user.getId();
        } else {
            // Fallback for unexpected principal types (e.g., simple string ID or anonymous)
            String name = authentication.getName();
            if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
            }
            try {
                userId = Long.parseLong(name);
            } catch (NumberFormatException ex) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid ID format");
            }
        }

        // Always load from DB to ensure we are working with a managed entity for updates
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
