package software.decibel.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Track;
import software.decibel.enums.Visibility;

public interface TrackRepository extends JpaRepository<Track, Long> {

  Page<Track> findByUploaderId(Long uploaderId, Pageable pageable);

  Page<Track> findByUploaderIdAndVisibility(
      Long uploaderId, Visibility visibility, Pageable pageable);
}
