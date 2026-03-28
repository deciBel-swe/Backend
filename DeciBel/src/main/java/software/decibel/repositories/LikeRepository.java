package software.decibel.repositories;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import software.decibel.entities.Like;
import software.decibel.entities.Track;
import software.decibel.entities.User;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserAndTrack(User user, Track track);

    Optional<Like> findByUserAndTrack(User user, Track track);

  // fixes typecast issue keep query
  @Query("SELECT l.track.id FROM Like l WHERE l.user.id = :userId")
  List<Long> findTrackIdsByUserId(Long userId);

  @Query("SELECT l.track FROM Like l WHERE l.user.id = :userId")
  Page<Track> findLikedTracksByUserId(Long userId, Pageable pageable);

  boolean existsByUserIdAndTrackId(Long userId, Long trackId);
}
