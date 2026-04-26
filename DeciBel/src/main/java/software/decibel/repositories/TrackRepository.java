package software.decibel.repositories;

import java.util.List;
import java.util.Optional;
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

    int countByUploaderId(Long userId);

    boolean existsBySlug(String slug);

    @Query("SELECT COALESCE(SUM(t.playCount), 0) FROM Track t")
    Long sumPlayCount();

    @Query("SELECT COALESCE(AVG(t.playThroughRate), 0) FROM Track t")
    Double averagePlayThroughRate();

    @Query("SELECT t.id FROM Track t WHERE t.slug = :slug")
    Optional<Long> findTrackIdBySlug(@Param("slug") String slug);

  // Genre Station – Discover Tracks with genres similar to user's genres
  // Filtering:
  // - Include tracks that match the user's genres (songs they listen to with that genres)
  // - Only include tracks that are:
  //     Public
  //     Published
  //   UPLOADING FINISHED
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
    WHERE LOWER(t.genre) IN (
        SELECT DISTINCT LOWER(t2.genre)
        FROM Track t2
        JOIN TrackLike l ON l.track.id = t2.id
        WHERE l.user.id = :userId
    )
    AND t.visibility = 'PUBLIC'
    AND t.published = true
    AND t.state = 'FINISHED'
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
  Page<Track> findGenreStation(@Param("userId") Long userId, Pageable pageable);

  // Artist Station – Discover Tracks based on genres of user's followed artists
  //
  // Filtering:
  // - Derive genres from tracks uploaded by user's followed artists
  // - Return tracks matching those genres from ANY artist except:
  //     Followed Artists
  //     The current user
  // - Only include tracks that are:
  //     Public
  //     Published
  //   UPLOADING FINISHED
  // - Exclude tracks that are:
  //     Already liked by the user
  //     Already reposted by the user
  //     From users blocked by the current user
  //     From users who have blocked the current user
  //
  // Ordering (priority-based):
  // 1. Highest play count first
  // 2. Then higher play-through rate
  // 3. Then higher like count
  // 4. Then higher repost count
  // 5. Then higher comment count
  @Query(
"""
    SELECT t FROM Track t
    WHERE LOWER(t.genre) IN (
        SELECT DISTINCT LOWER(ft.genre)
        FROM Track ft
        WHERE ft.uploader.id IN (
            SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId
        )
        AND ft.visibility = 'PUBLIC'
        AND ft.published = true
    )
    AND t.visibility = 'PUBLIC'
    AND t.published = true
    AND t.state = 'FINISHED'
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
    AND t.uploader.id NOT IN (
        SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId
    )
    ORDER BY
        t.playCount DESC,
        t.playThroughRate DESC,
        t.likeCount DESC,
        t.repostCount DESC,
        t.commentCount DESC
""")
  Page<Track> findArtistStation(@Param("userId") Long userId, Pageable pageable);

  // Likes Station – Discover Tracks based on tags of user's liked tracks
  //
  // Filtering:
  // - Derive tags from tracks the current user has liked
  // - Return tracks that share any of those tags
  // - Only include tracks that are:
  //     Public
  //     Published
  //     FINISHED
  // - Exclude tracks that are:
  //     Uploaded by the current user
  //     Already liked by the user
  //     Already reposted by the user
  //     From users blocked by the current user
  //     From users who have blocked the current user
  //
  // Ordering (priority-based):
  // 1. Highest play count first
  // 2. Then higher play-through rate
  // 3. Then higher like count
  // 4. Then higher repost count
  // 5. Then higher comment count
  @Query(
"""
    SELECT t FROM Track t
    JOIN t.tags tag
    WHERE tag.tagId IN (
        SELECT lt.tagId FROM Track lt2
        JOIN lt2.tags lt
        WHERE lt2.id IN (
            SELECT tl.track.id FROM TrackLike tl WHERE tl.user.id = :userId
        )
    )
    AND t.visibility = 'PUBLIC'
    AND t.published = true
    AND t.state = 'FINISHED'
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
    GROUP BY t
    ORDER BY
        t.playCount DESC,
        t.playThroughRate DESC,
        t.likeCount DESC,
        t.repostCount DESC,
        t.commentCount DESC
""")
  Page<Track> findLikesStation(@Param("userId") Long userId, Pageable pageable);

    @Query("SELECT t FROM Track t WHERE t.visibility = 'PUBLIC' AND t.published = true ORDER BY (t.likeCount + t.repostCount) DESC")
    Page<Track> findAllTrending(Pageable pageable);

    @Query("SELECT t FROM Track t WHERE t.uploader.id IN :uploaderIds AND t.visibility = 'PUBLIC' AND t.published = true")
    Page<Track> findByUploaderIdInAndVisibilityPublicAndPublishedTrue(List<Long> uploaderIds, Pageable pageable);

    @Query("""
        SELECT t FROM Track t
        WHERE LOWER(t.title) LIKE LOWER(CONCAT('%', :query, '%'))
        AND t.visibility = 'PUBLIC'
        AND t.published = true
        AND (:userId IS NULL OR NOT EXISTS (
            SELECT 1 FROM Block b
            WHERE (b.blocker.id = :userId AND b.blocked.id = t.uploader.id)
            OR (b.blocker.id = t.uploader.id AND b.blocked.id = :userId)
        ))
    """)
    Page<Track> searchPublicTracks(@Param("query") String query, @Param("userId") Long userId, Pageable pageable);
}
