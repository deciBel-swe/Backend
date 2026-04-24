package software.decibel.services;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Subscription;
import software.decibel.entities.User;
import software.decibel.enums.SubscriptionStatus;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.SubscriptionRepository;
import software.decibel.repositories.UserRepository;

@Slf4j
@Service
@RequiredArgsConstructor
public class CleanupSchedylerService {

    private final SubscriptionRepository subscriptionRepository;
    private final AuthIdentityRepository authIdentityRepository;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 60 * 10, timeUnit = TimeUnit.SECONDS)
    @Transactional
    public void cleanupStaleSubscriptions() {
        log.info("[SCHEDULER] Running stale subscription cleanup...");

        // Give users 30 minutes to complete checkout before deleting the pending subscription
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(10);

        List<Subscription> staleSubscriptions = subscriptionRepository
                .findByStatusAndUpdatedAtBefore(SubscriptionStatus.TRIALING, threshold);

        if (!staleSubscriptions.isEmpty()) {
            // Delete the local subscription records so the user can try checking out again
            subscriptionRepository.deleteAll(staleSubscriptions);
            log.info("[SCHEDULER] Removed {} stale checkout subscriptions.", staleSubscriptions.size());
        }
    }

    @Scheduled(fixedRate = 6, timeUnit = TimeUnit.HOURS)
    @Transactional
    public void cleanupUnverifiedUsers() {
        log.info("[SCHEDULER] Running unverified user cleanup...");

        // Give users a 24-hour grace period to verify their email
        LocalDateTime threshold = LocalDateTime.now().minusHours(6);

        List<AuthIdentity> unverifiedIdentities = authIdentityRepository
                .findUnverifiedOlderThan(threshold);

        int deletedCount = 0;
        for (AuthIdentity auth : unverifiedIdentities) {
            User user = auth.getUser();

            // Delete associated records manually to prevent foreign key constraints
            // (Since CascadeType.ALL wasn't fully defined on the User entity mapping for Auth)
            subscriptionRepository.findByUserId(user.getId())
                    .ifPresent(subscriptionRepository::delete);
            authIdentityRepository.delete(auth);
            userRepository.delete(user);

            deletedCount++;
        }

        if (deletedCount > 0) {
            log.info("[SCHEDULER] Deleted {} unverified user accounts.", deletedCount);
        }
    }

}
