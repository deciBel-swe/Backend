package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import jakarta.transaction.Transactional;
import software.decibel.entities.FcmToken;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    List<FcmToken> findAllByUserId(Long userId);

    Optional<FcmToken> findByUserIdAndToken(Long userId, String token);

    void deleteByUserIdAndToken(Long userId, String token);

}
