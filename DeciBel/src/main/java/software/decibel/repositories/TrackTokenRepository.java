package software.decibel.repositories;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.TrackToken;

public interface TrackTokenRepository extends JpaRepository<TrackToken, Long> {

    Optional<TrackToken> findByTrackIdAndIsDeletedFalse(Long trackId);

    Optional<TrackToken> findByTokenAndIsDeletedFalse(String token);
}
