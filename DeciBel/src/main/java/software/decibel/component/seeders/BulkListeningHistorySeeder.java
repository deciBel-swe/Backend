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
import software.decibel.entities.ListeningHistory;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.ListeningHistoryRepository;
import software.decibel.repositories.TrackRepository;

/**
 * Seeds listening history for the 20 bulk users.
 *
 * Mirrors the two-step flow in TrackService exactly:
 *
 * Step 1 — recordTrackPlay(): save a history row with completed=false,
 * increment track.playCount. Step 2 — recordTrackCompletion(): flip
 * completed=true on the row, increment track.completedPlayCount (capped at
 * playCount), recalculate track.playThroughRate.
 *
 * Each user gets 3–5 play entries. The first two entries per user are treated
 * as full listens (both steps applied); the rest are partial plays (step 1
 * only, completed=false).
 *
 * Order 12 — runs after BulkBlockSeeder (Order 11). Tracks must already exist
 * (BulkTrackSeeder, Order 7).
 */
@Component
@Order(12)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkListeningHistorySeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final AuthIdentityRepository authIdentityRepository;
    private final ListeningHistoryRepository listeningHistoryRepository;
    private final TrackRepository trackRepository;

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        if (listeningHistoryRepository.count() > 0) {
            log.info("[BulkListeningHistorySeeder] Listening history already exists — skipping.");
            return;
        }

        List<User> users = loadAllBulkUsers();
        if (users.isEmpty()) {
            log.warn("[BulkListeningHistorySeeder] No bulk users found — skipping.");
            return;
        }

        List<Track> tracks = trackRepository.findAll();
        if (tracks.isEmpty()) {
            log.warn("[BulkListeningHistorySeeder] No tracks found — skipping.");
            return;
        }

        int created = 0;

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);
            int entryCount = 3 + (i % 3); // 3, 4, or 5 entries per user

            for (int e = 0; e < entryCount; e++) {
                Track track = tracks.get((i * 2 + e * 3) % tracks.size());
                boolean isFullListen = e < 2; // first two entries per user are full listens

                // ── Step 1: recordTrackPlay() ─────────────────────────────────
                ListeningHistory history = listeningHistoryRepository.save(
                        ListeningHistory.builder()
                                .user(user)
                                .track(track)
                                .completed(false)
                                .build());

                track.setPlayCount(track.getPlayCount() + 1);

                // ── Step 2: recordTrackCompletion() ───────────────────────────
                if (isFullListen) {
                    history.setCompleted(true);
                    listeningHistoryRepository.save(history);

                    if (track.getCompletedPlayCount() < track.getPlayCount()) {
                        track.setCompletedPlayCount(track.getCompletedPlayCount() + 1);
                    }
                }

                // Recalculate playThroughRate after every play (matches TrackService logic)
                track.setPlayThroughRate(track.getPlayCount() > 0
                        ? (double) track.getCompletedPlayCount() / track.getPlayCount()
                        : 0.0);

                trackRepository.save(track);
                created++;
            }

            log.info("[BulkListeningHistorySeeder] Seeded {} history entries for user {}/{}",
                    entryCount, i + 1, users.size());
        }

        log.info("[BulkListeningHistorySeeder] Done. Total entries seeded: {}", created);
    }

    private List<User> loadAllBulkUsers() {
        return IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "bulk.user" + i + "@inbox.testmail.app")
                .map(email -> authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(
                        email, AuthProvider.LOCAL, AuthType.PASSWORD)
                .map(AuthIdentity::getUser)
                .orElse(null))
                .filter(u -> u != null)
                .collect(Collectors.toList());
    }
}
