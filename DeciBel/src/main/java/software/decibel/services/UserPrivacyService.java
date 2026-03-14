package software.decibel.services;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import software.decibel.dtos.PrivacyUpdateRequest;
import software.decibel.dtos.PrivacyUpdateResponse;
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

        // The JwtAuthenticationFilter sets the full User object as the principal
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }

        // Fallback for unexpected principal types (e.g., simple string ID from older filter versions)
        String name = authentication.getName();
        if (name == null || name.isBlank() || "anonymousUser".equalsIgnoreCase(name)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        final long userId;
        try {
            userId = Long.parseLong(name);
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid ID format");
        }
        // Load from DB if only ID is available in the principal
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
