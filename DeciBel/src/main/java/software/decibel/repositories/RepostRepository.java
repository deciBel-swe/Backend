package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Repost;
import software.decibel.entities.Track;
import software.decibel.entities.User;

import java.util.Optional;

public interface RepostRepository extends JpaRepository<Repost, Long> {

    boolean existsByUserAndTrack(User user, Track track);

    Optional<Repost> findByUserAndTrack(User user, Track track);
}
