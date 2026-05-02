package software.decibel.component.seeders;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.TrackAccess;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.SlugUtility;

@Component
@Order(7)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkTrackSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final AuthIdentityRepository authIdentityRepository;

    // Defines how many tracks each bulk user gets — index = user number (1-based)
    // 0 means no tracks for that user
    private static final int[] TRACK_COUNTS = {
        3, 2, 1, 3, 0, // users 1–5
        2, 1, 0, 3, 2, // users 6–10
        1, 0, 3, 2, 1, // users 11–15
        0, 3, 2, 1, 0 // users 16–20
    };

    private static final String[][] TRACK_DATA = {
        {"Neon Nights", "Synthwave", "A journey through a futuristic cityscape."},
        {"Mountain Echoes", "Ambient", "Spacious soundscapes inspired by nature."},
        {"Rainy Coffee Shop", "Lo-Fi", "Perfect background music for studying."},
        {"Pulse Drive", "House", "High-energy house track perfect for dancing."},
        {"Funk Theory", "Funk", "Groovy funk with funky basslines."},
        {"Synthscape", "Electronic", "Experimental synth-based electronic music."},
        {"Bass Injection", "House", "Deep house with emphasised basslines."},
        {"Desert Wind", "Ambient", "Slow drifting tones from the open desert."},
        {"City Lights", "Jazz", "Late night jazz inspired by city walks."},
        {"Echo Chamber", "Indie", "Reverb-heavy indie vibes with soft vocals."},};

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        int totalCreated = 0;

        for (int i = 1; i <= 20; i++) {
            int trackCount = TRACK_COUNTS[i - 1];

            if (trackCount == 0) {
                log.info("[BulkTrackSeeder] User {}/20 — no tracks assigned.", i);
                continue;
            }

            String email = "bulk.user" + i + "@decibel.dev";

            User user = authIdentityRepository
                    .findByEmailIgnoreCaseAndProviderAndType(email, AuthProvider.LOCAL, AuthType.PASSWORD)
                    .map(identity -> identity.getUser())
                    .orElse(null);

            if (user == null) {
                log.warn("[BulkTrackSeeder] User {} not found — skipping.", email);
                continue;
            }

            // Check if this user already has tracks seeded
            if (trackRepository.countByUploaderId(user.getId()) > 0) {
                log.info("[BulkTrackSeeder] User {} already has tracks — skipping.", email);
                continue;
            }

            List<Track> seeded = new ArrayList<>();
            for (int t = 0; t < trackCount; t++) {
                String[] data = TRACK_DATA[(i + t) % TRACK_DATA.length];
                Track track = buildTrack(user, data[0], data[1], data[2]);
                trackRepository.save(track);
                seeded.add(track);
                totalCreated++;
            }

            // Sync trackCount on the user
            user.setTrackCount(user.getTrackCount() + seeded.size());
            userRepository.save(user);

            log.info("[BulkTrackSeeder] Seeded {} track(s) for user {}/20: {}",
                    seeded.size(), i, email);
        }

        if (totalCreated == 0) {
            log.info("[BulkTrackSeeder] All bulk tracks already exist — skipping.");
        } else {
            log.info("[BulkTrackSeeder] Done. {} total tracks created.", totalCreated);
        }
    }

    private Track buildTrack(User uploader, String title, String genre, String description) {
        String slug = SlugUtility.generateUniqueSlug(title, trackRepository::existsBySlug);

        return Track.builder()
                .title(title)
                .uploader(uploader)
                .genre(genre)
                .description(description)
                .durationSeconds(180 + (int) (Math.random() * 180)) // 3–6 min
                .releaseDate(LocalDate.now())
                .state(TrackState.FINISHED)
                .access(TrackAccess.PLAYABLE)
                .visibility(Visibility.PUBLIC)
                .trackUrl("https://example.com/audio/" + slug + ".mp3")
                .trackPreviewUrl("https://example.com/audio/previews/" + slug + "-preview.mp3")
                .coverUrl("https://example.com/covers/" + slug + ".jpg")
                .waveformUrl("https://example.com/waveforms/" + slug + ".json")
                .slug(slug)
                .published(true)
                .publishedAt(LocalDateTime.now())
                .likeCount(0)
                .repostCount(0)
                .playCount(0)
                .build();
    }
}
