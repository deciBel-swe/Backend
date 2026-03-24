package software.decibel.services.user;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.decibel.dtos.user.UserProfileTokenResponse;
import software.decibel.entities.User;
import software.decibel.entities.UserProfileToken;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.UserProfileTokenRepository;
import software.decibel.repositories.UserRepository;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserProfileTokenService {

    private final UserProfileTokenRepository userProfileTokenRepository;
    private final UserRepository userRepository;

    // Returns the active token for the user, or throws if none exists
    public UserProfileTokenResponse getActiveToken(Long userId) {
        // Check user exists
        userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        UserProfileToken token = userProfileTokenRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .orElseThrow(() -> new ResourceNotFoundException("No active profile token for user " + userId));

        return new UserProfileTokenResponse(token.getToken());
    }

    // Soft deletes existing token and generates a new one
    @Transactional
    public UserProfileTokenResponse regenerateToken(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));

        // Soft delete existing active token if present
        userProfileTokenRepository
                .findByUserIdAndIsDeletedFalse(userId)
                .ifPresent(t -> {
                    t.setDeleted(true);
                    userProfileTokenRepository.save(t);
                });

        // Generate new token
        String tokenString = UUID.randomUUID().toString();
        UserProfileToken newToken = UserProfileToken.builder()
                .user(user)
                .token(tokenString)
                .build();

        return new UserProfileTokenResponse(userProfileTokenRepository.save(newToken).getToken());
    }

}
