package software.decibel.repositories;

import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import software.decibel.entities.Repost;
import software.decibel.entities.Track;
import software.decibel.entities.User;

public interface RepostRepository extends JpaRepository<Repost, Long> {

    boolean existsByUserAndTrack(User user, Track track);

    Optional<Repost> findByUserAndTrack(User user, Track track);

  // keep query fixes type cast issue
  @Query("SELECT l.track.id FROM Repost l WHERE l.user.id = :userId")
  Set<Long> findTrackIdsByUserId(Long userId);

  boolean existsByUserIdAndTrackId(Long userId, Long id);

  @Query("SELECT r.track FROM Repost r WHERE r.user.id = :userId")
  Page<Track> findRepostedTracksByUserId(Long userId, Pageable pageable);
}
