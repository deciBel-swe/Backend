package software.decibel.repositories;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import software.decibel.entities.Track;
import software.decibel.enums.Visibility;

public interface TrackRepository extends JpaRepository<Track, Long> {

    Page<Track> findByUploaderId(Long uploaderId, Pageable pageable);

    Page<Track> findByUploaderIdAndVisibility(
            Long uploaderId, Visibility visibility, Pageable pageable);

    // function to find distinct genres of tracks liked by user
    @Query("SELECT DISTINCT t.genre FROM Track t JOIN TrackLike l ON l.track.id = t.id WHERE l.user.id = :userId")
    List<String> findGenresOfLikedTracksByUserId(Long userId);

    boolean existsBySlug(String slug);

  // Genre Station – Discover Tracks by Genre

  // Filtering:
  // - Include tracks that match the provided genre
  // - Only include tracks that are:
  //     Public
  //     Published
  // - Exclude tracks that are:
  //     Uploaded by the current user
  //     Already liked by the user
  //     Already reposted by the user
  //     From users blocked by the current user
  //     From users who have blocked the current user

  // Ordering (priority-based):
  // 1. Highest play count first
  // 2. Then higher play-through rate
  // 3. Then higher like count
  // 4. Then higher repost count
  // 5. Then higher comment count
  @Query(
"""
    SELECT t FROM Track t
    WHERE LOWER(t.genre) = LOWER(:genre)
    AND t.visibility = 'PUBLIC'
    AND t.published = true
    AND t.uploader.id != :userId
    AND t.id NOT IN (
        SELECT tl.track.id FROM TrackLike tl WHERE tl.user.id = :userId
    )
    AND t.id NOT IN (
        SELECT tr.track.id FROM TrackRepost tr WHERE tr.user.id = :userId
    )
    AND t.uploader.id NOT IN (
        SELECT b.blocked.id FROM Block b WHERE b.blocker.id = :userId
    )
    AND t.uploader.id NOT IN (
        SELECT b.blocker.id FROM Block b WHERE b.blocked.id = :userId
    )
    ORDER BY
        t.playCount DESC,
        t.playThroughRate DESC,
        t.likeCount DESC,
        t.repostCount DESC,
        t.commentCount DESC
""")
  Page<Track> findGenreStation(
      @Param("genre") String genre, @Param("userId") Long userId, Pageable pageable);
}
