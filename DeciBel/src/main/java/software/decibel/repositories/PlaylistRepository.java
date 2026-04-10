package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Playlist;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    Optional<Playlist> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Page<Playlist> findByUserId(Long userId, Pageable pageable);
 
    Page<Playlist> findByUserIdAndIsPrivateFalse(Long userId, Pageable pageable);
}
