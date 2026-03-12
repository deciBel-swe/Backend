package software.decibel.services;

import software.decibel.dtos.PrivacyUpdateRequest;
import software.decibel.dtos.PrivacyUpdateResponse;
import software.decibel.entities.User;
import software.decibel.repositories.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

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
        // TODO: Add the authentication checks when authentication is implemented. For now, we will assume the user is authenticated and the principal is the user ID.
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized");
        }

        String principal = authentication.getName();

        // Assuming the principal is the user ID for simplicity, For Now...
        return userRepository.findById(Long.parseLong(principal))
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
    }
}
