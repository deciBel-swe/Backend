package software.decibel.config;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
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

/**
 * Seeds sample tracks for producer_user for development and testing purposes. Runs after
 * DevTrackSeeder to ensure no conflicts.
 */
@Component
@Order(4)
@RequiredArgsConstructor
@Slf4j
public class DevTrack2Seeder implements CommandLineRunner {

  private final TrackRepository trackRepository;
  private final UserRepository userRepository;
  private final TagRepository tagRepository;

  @Override
  @Transactional
  public void run(String... args) throws Exception {
    // Check if we've already seeded these tracks (count > 3 from DevTrackSeeder)
    if (trackRepository.count() > 3) {
      log.info("Additional tracks already exist. Skipping DevTrack2 seeding.");
      return;
    }

    log.info("Seeding sample tracks for producer user...");

    // Get or create producer user
    User producerUser = getOrCreateUser("demo_user2", "Demo User 2");

    // Seed Tags
    Tag electronic = getOrCreateTag("Electronic");
    Tag house = getOrCreateTag("House");
    Tag funkk = getOrCreateTag("Funk");

    // ──  TRACKS (House, Electronic, Funk) ──────────────────
    seedTrack(
        producerUser,
        "Pulse Drive",
        "House",
        "High-energy house track perfect for dancing.",
        320,
        List.of(house, electronic),
        "https://example.com/audio/pulse-drive.mp3",
        "https://example.com/covers/pulse-drive.jpg");

    seedTrack(
        producerUser,
        "Funk Theory",
        "Funk",
        "Groovy funk with funky basslines and upbeat energy.",
        260,
        List.of(funkk, electronic),
        "https://example.com/audio/funk-theory.mp3",
        "https://example.com/covers/funk-theory.jpg");

    seedTrack(
        producerUser,
        "Synthscape",
        "Electronic",
        "Experimental synth-based electronic music.",
        380,
        List.of(electronic),
        "https://example.com/audio/synthscape.mp3",
        "https://example.com/covers/synthscape.jpg");

    seedTrack(
        producerUser,
        "Bass Injection",
        "House",
        "Deep house with emphasised basslines.",
        300,
        List.of(house, electronic),
        "https://example.com/audio/bass-injection.mp3",
        "https://example.com/covers/bass-injection.jpg");

    // Update track count for producerUser
    producerUser.setTrackCount(4);
    userRepository.save(producerUser);

    log.info("Sample tracks for producer user seeded successfully.");
    log.info("Total tracks: {} (demo_user: 3, producer_user: 4)", trackRepository.count());
  }

  private void seedTrack(
      User uploader,
      String title,
      String genre,
      String description,
      int duration,
      List<Tag> tags,
      String audioUrl,
      String coverUrl) {

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
    return tagRepository
        .findByTitle(title)
        .orElseGet(() -> tagRepository.save(Tag.builder().title(title).build()));
  }

  private User getOrCreateUser(String username, String displayName) {
    return userRepository
        .findByUsername(username)
        .orElseGet(
            () -> {
              User newUser =
                  User.builder().username(username).displayName(displayName).trackCount(0).build();
              return userRepository.save(newUser);
            });
  }
}
