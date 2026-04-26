package software.decibel.services.user;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.UserRepository;

@Service
@RequiredArgsConstructor
public class UserService {

  private final UserRepository userRepository;
  private final software.decibel.repositories.BlockRepository blockRepository;

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
   * Check if there is any block relationship between two users.
   */
  public boolean isBlockRelationshipActive(Long userId1, Long userId2) {
    if (userId1 == null || userId2 == null) return false;
    return blockRepository.existsByBlocker_IdAndBlocked_Id(userId1, userId2) ||
           blockRepository.existsByBlocker_IdAndBlocked_Id(userId2, userId1);
  }

  /**
   * Check if blockerId has blocked blockedId.
   */
  public boolean hasBlocked(Long blockerId, Long blockedId) {
    if (blockerId == null || blockedId == null) return false;
    return blockRepository.existsByBlocker_IdAndBlocked_Id(blockerId, blockedId);
  }
}
