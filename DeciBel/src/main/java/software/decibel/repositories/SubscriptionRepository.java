package software.decibel.repositories;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import software.decibel.entities.Subscription;

public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {

    Optional<Subscription> findByUserId(Long userId);

    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);
}
