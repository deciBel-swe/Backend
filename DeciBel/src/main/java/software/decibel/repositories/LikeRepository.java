package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Like;
import software.decibel.entities.Track;
import software.decibel.entities.User;

import java.util.Optional;

public interface LikeRepository extends JpaRepository<Like, Long> {

    boolean existsByUserAndTrack(User user, Track track);

    Optional<Like> findByUserAndTrack(User user, Track track);
}
