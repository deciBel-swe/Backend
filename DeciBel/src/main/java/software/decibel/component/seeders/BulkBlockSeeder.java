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
import software.decibel.entities.Block;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.UserRepository;

/**
 * Seeds block relationships for the 20 bulk users. Every 5th user (users 5, 10,
 * 15, 20) blocks the user 2 positions ahead. Also removes any existing follow
 * relationships in both directions, mirroring BlockService behaviour.
 *
 * Order 11 — must run after BulkFollowSeeder (Order 10) so follow relationships
 * exist to be cleaned up.
 */
@Component
@Order(11)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkBlockSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final AuthIdentityRepository authIdentityRepository;
    private final BlockRepository blockRepository;
    private final FollowRepository followRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (blockRepository.count() > 0) {
            log.info("[BulkBlockSeeder] Blocks already exist — skipping.");
            return;
        }

        List<User> users = loadAllBulkUsers();
        if (users.size() < 2) {
            log.warn("[BulkBlockSeeder] Not enough bulk users found — skipping.");
            return;
        }

        int created = 0;

        for (int i = 4; i < users.size(); i += 5) { // indices 4, 9, 14, 19 → users 5, 10, 15, 20
            User blocker = users.get(i);
            User blocked = users.get((i + 2) % users.size());

            if (blocker.getId().equals(blocked.getId())) {
                continue;
            }

            if (blockRepository.existsByBlockerAndBlocked(blocker, blocked)) {
                continue;
            }

            blockRepository.save(Block.builder()
                    .blocker(blocker)
                    .blocked(blocked)
                    .build());

            removeFollowIfPresent(blocker, blocked);
            removeFollowIfPresent(blocked, blocker);

            created++;
            log.info("[BulkBlockSeeder] Seeded block: user {} → user {}",
                    blocker.getId(), blocked.getId());
        }

        if (created == 0) {
            log.info("[BulkBlockSeeder] All bulk blocks already exist — skipping.");
        } else {
            log.info("[BulkBlockSeeder] Done. Blocks seeded: {}", created);
        }
    }

    /**
     * Removes a follow relationship and updates denormalised counts on both
     * users, mirroring BlockService.removeFollowRelationship().
     */
    private void removeFollowIfPresent(User follower, User following) {
        followRepository.findByFollowerAndFollowing(follower, following).ifPresent(follow -> {
            followRepository.delete(follow);
            following.setFollowerCount(Math.max(0, following.getFollowerCount() - 1));
            follower.setFollowingCount(Math.max(0, follower.getFollowingCount() - 1));
            userRepository.save(following);
            userRepository.save(follower);
        });
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
