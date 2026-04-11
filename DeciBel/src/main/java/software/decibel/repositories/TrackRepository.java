package software.decibel.repositories;

import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
    
    @Query("SELECT t FROM Track t WHERE t.visibility = 'PUBLIC' AND t.published = true ORDER BY (t.likeCount + t.repostCount) DESC")
    Page<Track> findAllTrending(Pageable pageable);
}
