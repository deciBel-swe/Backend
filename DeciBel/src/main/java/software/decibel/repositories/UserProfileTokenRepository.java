package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.UserProfileToken;
import java.util.Optional;

public interface UserProfileTokenRepository extends JpaRepository<UserProfileToken, Long> {

    Optional<UserProfileToken> findByUserIdAndIsDeletedFalse(Long userId);

    Optional<UserProfileToken> findByTokenAndIsDeletedFalse(String token);

}
