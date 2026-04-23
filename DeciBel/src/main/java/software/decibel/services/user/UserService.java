package software.decibel.services.user;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final BlockRepository blockRepository;

  // Returns user entity by id and throws exception if not found

  public User getUserIfExistsById(Long userId) {
    return userRepository
        .findByIdAndIsBannedFalse(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found"));
  }

  public User getUserIfExistsByUsername(String username) {
    return userRepository
        .findByUsernameAndIsBannedFalse(username)
        .orElseThrow(() -> new ResourceNotFoundException("User with username " + username + " not found"));
  }

  /**
   * Checks if there is a mutual block relationship between two users.
   */
  public boolean isBlocked(Long userId1, Long userId2) {
    if (userId1 == null || userId2 == null || userId1.equals(userId2)) {
      return false;
    }
    return blockRepository.existsByBlocker_IdAndBlocked_Id(userId1, userId2)
        || blockRepository.existsByBlocker_IdAndBlocked_Id(userId2, userId1);
  }

  /**
   * Validates that an interaction between two users is allowed (no blocks).
   * Throws FORBIDDEN if blocked.
   */
  public void validateInteraction(Long actorId, Long targetId) {
    if (isBlocked(actorId, targetId)) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Action blocked due to privacy settings");
    }
  }

  /**
   * Validates that a user can see another user's content (no blocks).
   * Throws NOT_FOUND if blocked to prevent info leaks.
   */
  public void validateVisibility(Long viewerId, Long targetId, String resourceName) {
    if (isBlocked(viewerId, targetId)) {
      throw new ResourceNotFoundException(resourceName + " not found");
    }
  }
}
