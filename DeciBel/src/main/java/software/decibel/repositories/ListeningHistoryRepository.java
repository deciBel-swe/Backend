package software.decibel.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.ListeningHistory;

public interface ListeningHistoryRepository extends JpaRepository<ListeningHistory, Long> {

    Page<ListeningHistory> findByUserIdOrderByPlayedAtDesc(Long userId, Pageable pageable);
}
