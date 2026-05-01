package software.decibel.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import software.decibel.entities.Playlist;

public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    Optional<Playlist> findBySlug(String slug);

    boolean existsBySlug(String slug);

    Page<Playlist> findByUserId(Long userId, Pageable pageable);

    Page<Playlist> findByUserIdAndIsPrivateFalse(Long userId, Pageable pageable);

    @Query("SELECT p FROM Playlist p WHERE p.user.id IN :userIds AND p.isPrivate = false")
    Page<Playlist> findByUserIdInAndIsPrivateFalse(List<Long> userIds, Pageable pageable);

    @Modifying
    @Query(value = "DELETE FROM playlist_tracks WHERE track_id = :trackId", nativeQuery = true)
    void removeTrackFromAllPlaylists(@Param("trackId") Long trackId);

    @Query("""
        SELECT p FROM Playlist p
        WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%'))
        AND p.isPrivate = false
        AND (:userId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b
            WHERE (b.blocker.id = :userId AND b.blocked.id = p.user.id)
            OR (b.blocker.id = p.user.id AND b.blocked.id = :userId)
        ))
    """)
    Page<Playlist> searchPublicPlaylists(@Param("query") String query, @Param("userId") Long userId, Pageable pageable);

    @Query("SELECT p.id FROM Playlist p WHERE p.slug = :slug")
    Optional<Long> findIdBySlug(@Param("slug") String slug);

}
