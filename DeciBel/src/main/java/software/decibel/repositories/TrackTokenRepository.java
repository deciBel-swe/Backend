package software.decibel.repositories;

import java.util.List;
import java.util.Set;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import software.decibel.entities.TrackToken;
import software.decibel.projections.TrackTokenProjection;

public interface TrackTokenRepository extends JpaRepository<TrackToken, Long> {

    Optional<TrackToken> findByTrackIdAndIsDeletedFalse(Long trackId);

    Optional<TrackToken> findByTokenAndIsDeletedFalse(String token);

    @Query("SELECT t.track.id AS trackId, t.token AS token FROM TrackToken t WHERE t.track.id IN :trackIds AND t.isDeleted = false")
    List<TrackTokenProjection> findActiveTokensByTrackIds(@Param("trackIds") Set<Long> trackIds);
}
