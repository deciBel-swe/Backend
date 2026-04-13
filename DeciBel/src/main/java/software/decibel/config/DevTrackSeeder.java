package software.decibel.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.repositories.TagRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.SlugUtility;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Seeds sample tracks for the demo user for development and testing purposes.
 * Runs after DevUserSeeder to ensure the uploader exists.
 */
@Component
@Order(2)
@RequiredArgsConstructor
@Slf4j
public class DevTrackSeeder implements CommandLineRunner {

    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        String demoUsername = "demo_user";
        Optional<User> demoUserOpt = userRepository.findByUsername(demoUsername);

        if (demoUserOpt.isEmpty()) {
            log.warn("Demo user '{}' not found. Skipping track seeding.", demoUsername);
            return;
        }

        if (trackRepository.count() > 0) {
            log.info("Tracks already exist. Skipping track seeding.");
            return;
        }

        User demoUser = demoUserOpt.get();
        log.info("Seeding sample tracks for user '{}'...", demoUsername);

        // Seed Tags
        Tag electronic = getOrCreateTag("Electronic");
        Tag synthwave = getOrCreateTag("Synthwave");
        Tag ambient = getOrCreateTag("Ambient");
        Tag loFi = getOrCreateTag("Lo-Fi");

        // Track 1: Neon Nights
        seedTrack(demoUser, "Neon Nights", "Synthwave", 
            "A journey through a futuristic cityscape.", 
            245, List.of(electronic, synthwave), 
            "https://example.com/audio/neon-nights.mp3",
            "https://example.com/covers/neon-nights.jpg");

        // Track 2: Mountain Echoes
        seedTrack(demoUser, "Mountain Echoes", "Ambient", 
            "Spacious soundscapes inspired by nature.", 
            420, List.of(ambient), 
            "https://example.com/audio/mountain-echoes.mp3",
            "https://example.com/covers/mountain-echoes.jpg");

        // Track 3: Rainy Coffee Shop
        seedTrack(demoUser, "Rainy Coffee Shop", "Lo-Fi", 
            "Perfect background music for studying.", 
            180, List.of(loFi), 
            "https://example.com/audio/rainy-coffee.mp3",
            "https://example.com/covers/rainy-coffee.jpg");

        // Update user track count
        demoUser.setTrackCount(3);
        userRepository.save(demoUser);

        log.info("Sample tracks seeded successfully.");
    }

    private void seedTrack(User uploader, String title, String genre, String description, 
                           int duration, List<Tag> tags, String audioUrl, String coverUrl) {
        
        String slug = SlugUtility.generateUniqueSlug(title, trackRepository::existsBySlug);
        
        Track track = Track.builder()
                .title(title)
                .uploader(uploader)
                .genre(genre)
                .description(description)
                .durationSeconds(duration)
                .releaseDate(LocalDate.now())
                .state(TrackState.FINISHED)
                .visibility(Visibility.PUBLIC)
                .trackUrl(audioUrl)
                .coverUrl(coverUrl)
                .waveformUrl("https://example.com/waveforms/default.json")
                .slug(slug)
                .published(true)
                .publishedAt(LocalDateTime.now())
                .tags(tags)
                .likeCount(0)
                .repostCount(0)
                .playCount(0)
                .build();

        trackRepository.save(track);
    }

    private Tag getOrCreateTag(String title) {
        return tagRepository.findByTitle(title)
                .orElseGet(() -> tagRepository.save(Tag.builder().title(title).build()));
    }
}
