package software.decibel.repositories;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import software.decibel.entities.TrackToken;

public interface TrackTokenRepository extends JpaRepository<TrackToken, Long> {

    Optional<TrackToken> findByTrackIdAndIsDeletedFalse(Long trackId);

    Optional<TrackToken> findByTokenAndIsDeletedFalse(String token);

  // will return a map for each trackid and its active token
  @Query(
      "SELECT t.track.id, t.token FROM TrackToken t WHERE t.track.id IN :trackIds AND t.isDeleted = false")
  Map<Long, String> findActiveTokensByTrackIds(@Param("trackIds") Set<Long> trackIds);
}
