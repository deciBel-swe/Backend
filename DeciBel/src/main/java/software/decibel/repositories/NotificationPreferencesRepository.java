package software.decibel.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.NotificationPreferences;

import java.util.Optional;

public interface NotificationPreferencesRepository extends JpaRepository<NotificationPreferences, Long> {

    Optional<NotificationPreferences> findByUserId(Long userId);
}
