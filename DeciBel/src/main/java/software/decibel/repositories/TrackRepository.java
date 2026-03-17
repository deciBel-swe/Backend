package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Track;

public interface TrackRepository extends JpaRepository<Track, Long> {}
