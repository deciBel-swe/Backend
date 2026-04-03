package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Playlist;
import java.util.Optional;


public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    Optional<Playlist> findBySlug(String slug);
    boolean existsBySlug(String slug);
}
