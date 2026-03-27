package software.decibel.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import software.decibel.entities.Track;
import software.decibel.enums.Visibility;

import java.util.List;

public interface TrackRepository extends JpaRepository<Track, Long> {

  Page<Track> findByUploaderId(Long uploaderId, Pageable pageable);

  Page<Track> findByUploaderIdAndVisibility(
      Long uploaderId, Visibility visibility, Pageable pageable);

  // function to find distinct genres of tracks liked by user
  @Query("SELECT DISTINCT t.genre FROM Track t JOIN Like l ON l.track.id = t.id WHERE l.user.id = :userId")
  List<String> findGenresOfLikedTracksByUserId(Long userId);
}
