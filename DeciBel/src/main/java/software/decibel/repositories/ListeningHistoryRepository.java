package software.decibel.repositories;

import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import jakarta.persistence.LockModeType;
import software.decibel.entities.ListeningHistory;

public interface ListeningHistoryRepository extends JpaRepository<ListeningHistory, Long> {

    Page<ListeningHistory> findByUserIdOrderByPlayedAtDesc(Long userId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT h FROM ListeningHistory h WHERE h.user.id = :userId AND h.track.id = :trackId ORDER BY h.playedAt DESC LIMIT 1")
    Optional<ListeningHistory> findTopByUserIdAndTrackIdOrderByPlayedAtDesc(Long userId, Long trackId);

    Optional<ListeningHistory> findTopByUserIdAndTrackIdAndCompletedFalseOrderByPlayedAtDesc(Long userId, Long trackId);

    Optional<ListeningHistory> findTopByUserIdOrderByPlayedAtDesc(Long userId);
}
