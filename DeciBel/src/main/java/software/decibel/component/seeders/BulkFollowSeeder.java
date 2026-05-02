package software.decibel.component.seeders;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Follow;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.UserRepository;

/**
 * Seeds follow relationships for the 20 bulk users. Each user follows the next
 * 3 users in the list (wrapping around). Respects any pre-existing block
 * relationships.
 *
 * Order 10 — runs after BulkEngagementSeeder (Order 9).
 */
@Component
@Order(10)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkFollowSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final AuthIdentityRepository authIdentityRepository;
    private final FollowRepository followRepository;
    private final BlockRepository blockRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (followRepository.count() > 0) {
            log.info("[BulkFollowSeeder] Follows already exist — skipping.");
            return;
        }

        List<User> users = loadAllBulkUsers();
        if (users.size() < 2) {
            log.warn("[BulkFollowSeeder] Not enough bulk users found — skipping.");
            return;
        }

        int created = 0;

        for (int i = 0; i < users.size(); i++) {
            User follower = users.get(i);

            for (int offset = 1; offset <= 3; offset++) {
                User following = users.get((i + offset) % users.size());

                if (blockRepository.existsByBlocker_IdAndBlocked_Id(following.getId(), follower.getId())) {
                    log.debug("[BulkFollowSeeder] Skipping follow {} → {}: target has blocked follower",
                            follower.getId(), following.getId());
                    continue;
                }

                if (followRepository.existsByFollowerAndFollowing(follower, following)) {
                    continue;
                }

                followRepository.save(Follow.builder()
                        .follower(follower)
                        .following(following)
                        .build());

                following.setFollowerCount(following.getFollowerCount() + 1);
                follower.setFollowingCount(follower.getFollowingCount() + 1);
                userRepository.save(following);
                userRepository.save(follower);

                created++;
            }
        }

        log.info("[BulkFollowSeeder] Done. Follows seeded: {}", created);
    }

    private List<User> loadAllBulkUsers() {
        return IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "bulk.user" + i + "@decibel.dev")
                .map(email -> authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(
                        email, AuthProvider.LOCAL, AuthType.PASSWORD)
                .map(AuthIdentity::getUser)
                .orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toList());
    }
}
