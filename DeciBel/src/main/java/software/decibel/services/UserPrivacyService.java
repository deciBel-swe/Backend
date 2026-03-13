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
        User currentUser = resolveCurrentUser(authentication);

        currentUser.setPrivate(request.isPrivate());
        currentUser.setShowHistory(request.showHistory());

        return new PrivacyUpdateResponse(currentUser.isPrivate(), currentUser.isShowHistory());
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
        // Assuming the principal is the user ID for simplicity, For Now...
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
