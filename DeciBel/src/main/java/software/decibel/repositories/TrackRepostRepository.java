package software.decibel.repositories;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import software.decibel.entities.Track;
import software.decibel.entities.TrackRepost;
import software.decibel.entities.User;

public interface TrackRepostRepository extends JpaRepository<TrackRepost, Long> {

    boolean existsByUserAndTrack(User user, Track track);

    Optional<TrackRepost> findByUserAndTrack(User user, Track track);

    // keep query fixes type cast issue
    @Query("SELECT l.track.id FROM TrackRepost l WHERE l.user.id = :userId")
    Set<Long> findTrackIdsByUserId(Long userId);

    boolean existsByUserIdAndTrackId(Long userId, Long id);

    @Query("SELECT r.track FROM TrackRepost r WHERE r.user.id = :userId")
    Page<Track> findRepostedTracksByUserId(Long userId, Pageable pageable);

    Page<TrackRepost> findByUser(User user, Pageable pageable);

    @Modifying
    @Query("DELETE FROM TrackRepost r WHERE r.track.id = :trackId")
    void deleteAllByTrackId(@Param("trackId") Long trackId);

    @Query("""
        SELECT tr.user FROM TrackRepost tr 
        WHERE tr.track.id = :trackId
        AND (:currentUserId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b
            WHERE (b.blocker.id = :currentUserId AND b.blocked.id = tr.user.id)
            OR (b.blocker.id = tr.user.id AND b.blocked.id = :currentUserId)
        ))
    """)
    Page<User> findUsersByTrackIdWithBlocking(@Param("trackId") Long trackId, @Param("currentUserId") Long currentUserId, Pageable pageable);

    @Query("SELECT tr.user FROM TrackRepost tr WHERE tr.track.id = :trackId")
    Page<User> findUsersByTrackId(@Param("trackId") Long trackId, Pageable pageable);

    @Query("""
        SELECT tr FROM TrackRepost tr 
        WHERE tr.user.id IN :userIds 
        AND (:currentUserId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b 
            WHERE (b.blocker.id = :currentUserId AND b.blocked.id = tr.track.uploader.id)
            OR (b.blocker.id = tr.track.uploader.id AND b.blocked.id = :currentUserId)
        ))
        ORDER BY tr.repostedAt DESC
    """)
    Page<TrackRepost> findByUserIdInWithBlocking(@Param("userIds") List<Long> userIds, @Param("currentUserId") Long currentUserId, Pageable pageable);

    @Query("SELECT tr FROM TrackRepost tr WHERE tr.user.id IN :userIds ORDER BY tr.repostedAt DESC")
    Page<TrackRepost> findByUserIdIn(@Param("userIds") List<Long> userIds, Pageable pageable);
}
