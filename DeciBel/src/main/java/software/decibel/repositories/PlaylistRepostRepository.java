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
import software.decibel.entities.PlaylistRepost;
import software.decibel.entities.User;

public interface PlaylistRepostRepository extends JpaRepository<PlaylistRepost, Long> {

    Optional<PlaylistRepost> findByUserAndPlaylist(User user, Playlist playlist);

    Optional<PlaylistRepost> findByUserIdAndPlaylistId(Long userId, Long playlistId);

    boolean existsByUserAndPlaylist(User user, Playlist playlist);

    Page<PlaylistRepost> findByUser(User user, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PlaylistRepost r WHERE r.playlist.id = :playlistId")
    void deleteAllByPlaylistId(@Param("playlistId") Long playlistId);

    @Query("SELECT pr.playlist FROM PlaylistRepost pr WHERE pr.user.id = :userId")
    Page<Playlist> findRepostedPlaylistsByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
        SELECT pr.user FROM PlaylistRepost pr 
        WHERE pr.playlist.id = :playlistId
        AND (:currentUserId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b
            WHERE (b.blocker.id = :currentUserId AND b.blocked.id = pr.user.id)
            OR (b.blocker.id = pr.user.id AND b.blocked.id = :currentUserId)
        ))
    """)
    Page<User> findUsersByPlaylistIdWithBlocking(@Param("playlistId") Long playlistId, @Param("currentUserId") Long currentUserId, Pageable pageable);

    @Query("SELECT pr.user FROM PlaylistRepost pr WHERE pr.playlist.id = :playlistId")
    Page<User> findUsersByPlaylistId(@Param("playlistId") Long playlistId, Pageable pageable);

    @Query("""
        SELECT pr FROM PlaylistRepost pr 
        WHERE pr.user.id IN :userIds 
        AND (:currentUserId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b 
            WHERE (b.blocker.id = :currentUserId AND b.blocked.id = pr.playlist.user.id)
            OR (b.blocker.id = pr.playlist.user.id AND b.blocked.id = :currentUserId)
        ))
        ORDER BY pr.repostedAt DESC
    """)
    Page<PlaylistRepost> findByUserIdInWithBlocking(@Param("userIds") List<Long> userIds, @Param("currentUserId") Long currentUserId, Pageable pageable);

    @Query("SELECT pr FROM PlaylistRepost pr WHERE pr.user.id IN :userIds ORDER BY pr.repostedAt DESC")
    Page<PlaylistRepost> findByUserIdIn(@Param("userIds") List<Long> userIds, Pageable pageable);
}
