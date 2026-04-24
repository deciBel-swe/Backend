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

    @Query("SELECT p FROM Playlist p JOIN p.slugHistory pt WHERE pt.token = :token AND pt.isDeleted = false")
    Optional<Playlist> findByToken(@org.springframework.data.repository.query.Param("token") String token);

    @Query("SELECT p.id FROM Playlist p WHERE p.slug = :slug")
    Optional<Long> findIdBySlug(@org.springframework.data.repository.query.Param("slug") String slug);

    @Query("""
        SELECT p FROM Playlist p
        WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))
        AND p.isPrivate = false
        AND (:currentUserId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b
            WHERE (b.blocker.id = :currentUserId AND b.blocked.id = p.user.id)
            OR (b.blocker.id = p.user.id AND b.blocked.id = :currentUserId)
        ))
    """)
    Page<Playlist> searchPublicPlaylistsWithBlocking(String query, Long currentUserId, Pageable pageable);
}
