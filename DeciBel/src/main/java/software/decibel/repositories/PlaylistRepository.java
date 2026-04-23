package software.decibel.repositories;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
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

    @Query("SELECT p FROM Playlist p WHERE LOWER(p.title) LIKE LOWER(CONCAT('%', :query, '%')) AND p.isPrivate = false")
    Page<Playlist> searchPublicPlaylists(String query, Pageable pageable);

    @Query("SELECT p FROM Playlist p JOIN p.slugHistory pt WHERE pt.token = :token AND pt.isDeleted = false")
    Optional<Playlist> findByToken(@Param("token") String token);

    @Query("SELECT p.id FROM Playlist p WHERE p.slug = :slug")
    Optional<Long> findIdBySlug(@Param("slug") String slug);

}
