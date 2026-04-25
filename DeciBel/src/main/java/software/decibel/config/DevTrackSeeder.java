package software.decibel.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.entities.Tag;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.TrackAccess;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.repositories.TagRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.SlugUtility;

@Component
@Order(3)
@RequiredArgsConstructor
@Slf4j
public class DevTrackSeeder implements CommandLineRunner {

    private final TrackRepository trackRepository;
    private final UserRepository userRepository;
    private final TagRepository tagRepository;

  @Override
  @Transactional
  public void run(String... args) {
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

        Tag electronic = getOrCreateTag("Electronic");
        Tag synthwave = getOrCreateTag("Synthwave");
        Tag ambient = getOrCreateTag("Ambient");
        Tag loFi = getOrCreateTag("Lo-Fi");

    // 🎧 Track 1 → PLAYABLE
    seedTrack(
        demoUser,
        "Neon Nights",
        "Synthwave",
        "A journey through a futuristic cityscape.",
        245,
        List.of(electronic, synthwave),
        "https://example.com/audio/neon-nights.mp3",
        "https://example.com/audio/previews/neon-nights-preview.mp3",
        "https://example.com/covers/neon-nights.jpg",
        TrackAccess.PLAYABLE);

    // 🎧 Track 2 → PLAYABLE
    seedTrack(
        demoUser,
        "Mountain Echoes",
        "Ambient",
        "Spacious soundscapes inspired by nature.",
        420,
        List.of(ambient),
        "https://example.com/audio/mountain-echoes.mp3",
        "https://example.com/audio/previews/mountain-echoes-preview.mp3",
        "https://example.com/covers/mountain-echoes.jpg",
        TrackAccess.PLAYABLE);

    // 🎧 Track 3 → PREVIEW
    seedTrack(
        demoUser,
        "Rainy Coffee Shop",
        "Lo-Fi",
        "Perfect background music for studying.",
        180,
        List.of(loFi),
        "https://example.com/audio/rainy-coffee.mp3",
        "https://example.com/audio/previews/rainy-coffee-preview.mp3",
        "https://example.com/covers/rainy-coffee.jpg",
        TrackAccess.PREVIEW);

    // 🔒 (optional) If you want a blocked one instead, replace PREVIEW with BLOCKED

    demoUser.setTrackCount(3);
    demoUser.setFreeTracksLeft(0);
        userRepository.save(demoUser);

        log.info("Sample tracks seeded successfully.");
    }

  private void seedTrack(
      User uploader,
      String title,
      String genre,
      String description,
      int duration,
      List<Tag> tags,
      String audioUrl,
      String previewUrl,
      String coverUrl,
      TrackAccess access) {

        String slug = SlugUtility.generateUniqueSlug(title, trackRepository::existsBySlug);

    Track track =
        Track.builder()
            .title(title)
            .uploader(uploader)
            .genre(genre)
            .description(description)
            .durationSeconds(duration)
            .releaseDate(LocalDate.now())
            .state(TrackState.FINISHED)
            .access(access) // ✅ important
            .visibility(Visibility.PUBLIC)
            .trackUrl(audioUrl)
            .trackPreviewUrl(previewUrl) // ✅ added
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