package software.decibel.repositories;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.Subscription;
import software.decibel.enums.SubscriptionStatus;
import java.time.LocalDateTime;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);

    List<Subscription> findByStatusAndUpdatedAtBefore(SubscriptionStatus status, LocalDateTime threshold);
}
