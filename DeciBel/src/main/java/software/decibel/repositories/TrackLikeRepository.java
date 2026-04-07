package software.decibel.repositories;

import java.util.Optional;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import software.decibel.entities.Track;
import software.decibel.entities.TrackLike;
import software.decibel.entities.User;

public interface TrackLikeRepository extends JpaRepository<TrackLike, Long> {

    boolean existsByUserAndTrack(User user, Track track);

    Optional<TrackLike> findByUserAndTrack(User user, Track track);

    // fixes typecast issue keep query
    @Query("SELECT l.track.id FROM TrackLike l WHERE l.user.id = :userId")
    Set<Long> findTrackIdsByUserId(Long userId);

    @Query("SELECT l.track FROM TrackLike l WHERE l.user.id = :userId")
    Page<Track> findLikedTracksByUserId(Long userId, Pageable pageable);

    @Modifying
    @Query("DELETE FROM TrackLike r WHERE r.track.id = :trackId")
    void deleteAllByTrackId(@Param("trackId") Long trackId);

    boolean existsByUserIdAndTrackId(Long userId, Long trackId);

    @Query("SELECT tl.user FROM TrackLike tl WHERE tl.track.id = :trackId")
    Page<User> findUsersByTrackId(@Param("trackId") Long trackId, Pageable pageable);
}
