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

  // Returns user entity by id and throws exception if not found

  public User getUserIfExistsById(Long userId) {
    return userRepository
        .findById(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User with id " + userId + " not found"));
  }
}
