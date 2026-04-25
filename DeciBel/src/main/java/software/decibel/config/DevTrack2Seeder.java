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
import software.decibel.enums.TrackAccess;
import software.decibel.enums.TrackState;
import software.decibel.enums.Visibility;
import software.decibel.repositories.TagRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.utils.SlugUtility;

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
  public void run(String... args) {
    if (trackRepository.count() > 3) {
      log.info("Additional tracks already exist. Skipping DevTrack2 seeding.");
      return;
    }

    log.info("Seeding sample tracks for producer user...");

    User producerUser = getOrCreateUser("demo_user2", "Demo User 2");

    // 🔥 Set freeTracksLeft to 0
    producerUser.setFreeTracksLeft(0);

    Tag electronic = getOrCreateTag("Electronic");
    Tag house = getOrCreateTag("House");
    Tag funkk = getOrCreateTag("Funk");

    // ✅ PLAYABLE TRACKS (FULL ACCESS)
    seedTrack(
        producerUser,
        "Pulse Drive",
        "House",
        "High-energy house track perfect for dancing.",
        320,
        List.of(house, electronic),
        "https://example.com/audio/pulse-drive.mp3",
        "https://example.com/audio/previews/pulse-drive-preview.mp3",
        "https://example.com/covers/pulse-drive.jpg",
        TrackAccess.PLAYABLE);

    seedTrack(
        producerUser,
        "Funk Theory",
        "Funk",
        "Groovy funk with funky basslines and upbeat energy.",
        260,
        List.of(funkk, electronic),
        "https://example.com/audio/funk-theory.mp3",
        "https://example.com/audio/previews/funk-theory-preview.mp3",
        "https://example.com/covers/funk-theory.jpg",
        TrackAccess.PLAYABLE);

    seedTrack(
        producerUser,
        "Synthscape",
        "Electronic",
        "Experimental synth-based electronic music.",
        380,
        List.of(electronic),
        "https://example.com/audio/synthscape.mp3",
        "https://example.com/audio/previews/synthscape-preview.mp3",
        "https://example.com/covers/synthscape.jpg",
        TrackAccess.PREVIEW);

    // 🚫 BLOCKED TRACK (PREVIEW ONLY)
    seedTrack(
        producerUser,
        "Bass Injection",
        "House",
        "Deep house with emphasised basslines.",
        300,
        List.of(house, electronic),
        "https://example.com/audio/bass-injection.mp3",
        "https://example.com/audio/previews/bass-injection-preview.mp3",
        "https://example.com/covers/bass-injection.jpg",
        TrackAccess.BLOCKED);

    producerUser.setTrackCount(4);
    userRepository.save(producerUser);

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
                  User.builder()
                      .username(username)
                      .displayName(displayName)
                      .trackCount(0)
                      .freeTracksLeft(0) // ✅ ensure default
                      .build();
              return userRepository.save(newUser);
            });
  }
}