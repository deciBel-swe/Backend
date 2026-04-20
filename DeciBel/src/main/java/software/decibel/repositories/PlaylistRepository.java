package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import software.decibel.entities.Playlist;
import java.util.Optional;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {
    Optional<Playlist> findBySlug(String slug);
    boolean existsBySlug(String slug);
    Page<Playlist> findByUserId(Long userId, Pageable pageable);
 
    Page<Playlist> findByUserIdAndIsPrivateFalse(Long userId, Pageable pageable);

    @Query("SELECT p FROM Playlist p WHERE p.user.id IN :userIds AND p.isPrivate = false")
    Page<Playlist> findByUserIdInAndIsPrivateFalse(List<Long> userIds, Pageable pageable);

    @Query("SELECT p FROM Playlist p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) AND p.isPrivate = false")
    Page<Playlist> searchPublicPlaylists(String query, Pageable pageable);
}
