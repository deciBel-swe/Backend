package software.decibel.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistLike;
import software.decibel.entities.User;

public interface PlaylistLikeRepository extends JpaRepository<PlaylistLike, Long> {

    Optional<PlaylistLike> findByUserAndPlaylist(User user, Playlist playlist);

    boolean existsByUserAndPlaylist(User user, Playlist playlist);

    Page<PlaylistLike> findByUser(User user, Pageable pageable);

    @Modifying
    @Query("DELETE FROM PlaylistLike r WHERE r.playlist.id = :playlistId")
    void deleteAllByPlaylistId(@Param("playlistId") Long playlistId);

    @Query("SELECT pl.playlist FROM PlaylistLike pl WHERE pl.user.id = :userId")
    Page<Playlist> findLikedPlaylistsByUserId(@Param("userId") Long userId, Pageable pageable);
 
    // Returns the User entities who liked a given playlist
    @Query("SELECT pl.user FROM PlaylistLike pl WHERE pl.playlist.id = :playlistId")
    Page<User> findUsersByPlaylistId(@Param("playlistId") Long playlistId, Pageable pageable);

}
