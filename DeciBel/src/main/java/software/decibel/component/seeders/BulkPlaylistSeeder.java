package software.decibel.component.seeders;

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
import software.decibel.entities.Playlist;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.enums.PlaylistType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.utils.SlugUtility;

@Component
@Order(8)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkPlaylistSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final PlaylistRepository playlistRepository;
    private final TrackRepository trackRepository;
    private final AuthIdentityRepository authIdentityRepository;

    // Defines how many playlists each bulk user gets — index = user number (1-based)
    // Must match BulkUserSeeder's 20 users
    private static final int[] PLAYLIST_COUNTS = {
        2, 1, 0, 2, 1, // users 1–5
        0, 2, 1, 0, 2, // users 6–10
        1, 0, 2, 1, 0, // users 11–15
        2, 1, 0, 2, 1 // users 16–20
    };

    private static final String[][] PLAYLIST_DATA = {
        {"Late Night Drives", "Tracks for empty roads and city lights.", PlaylistType.PLAYLIST.name()},
        {"Morning Boost", "Start your day with high energy beats.", PlaylistType.PLAYLIST.name()},
        {"Chill Sundays", "Slow it down and breathe.", PlaylistType.PLAYLIST.name()},
        {"Deep Focus", "Block everything out and get in the zone.", PlaylistType.PLAYLIST.name()},
        {"Workout Mix", "Keep the energy up through every set.", PlaylistType.PLAYLIST.name()},
        {"Throwback Vibes", "Nostalgia in every beat.", PlaylistType.PLAYLIST.name()},
        {"Rainy Day Feels", "Perfect for grey skies and warm coffee.", PlaylistType.PLAYLIST.name()},
        {"Coding Session", "Background music for deep work.", PlaylistType.PLAYLIST.name()},};

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Load all available seeded tracks once to distribute across playlists
        List<Track> allTracks = trackRepository.findAll();

        if (allTracks.isEmpty()) {
            log.warn("[BulkPlaylistSeeder] No tracks found — run BulkTrackSeeder first. Skipping.");
            return;
        }

        int totalCreated = 0;

        for (int i = 1; i <= 20; i++) {
            int playlistCount = PLAYLIST_COUNTS[i - 1];

            if (playlistCount == 0) {
                log.info("[BulkPlaylistSeeder] User {}/20 — no playlists assigned.", i);
                continue;
            }

            String email = "bulk.user" + i + "@decibel.dev";

            User user = authIdentityRepository
                    .findByEmailIgnoreCaseAndProviderAndType(email, AuthProvider.LOCAL, AuthType.PASSWORD)
                    .map(identity -> identity.getUser())
                    .orElse(null);

            if (user == null) {
                log.warn("[BulkPlaylistSeeder] User {} not found — skipping.", email);
                continue;
            }

            // Skip if this user already has playlists
            if (!playlistRepository.findByUserId(user.getId(),
                    org.springframework.data.domain.Pageable.ofSize(1)).isEmpty()) {
                log.info("[BulkPlaylistSeeder] User {} already has playlists — skipping.", email);
                continue;
            }

            for (int p = 0; p < playlistCount; p++) {
                String[] data = PLAYLIST_DATA[(i + p) % PLAYLIST_DATA.length];
                String title = data[0];
                String desc = data[1];
                PlaylistType type = PlaylistType.valueOf(data[2]);

                // Pick 2–4 tracks from allTracks, rotated by user+playlist index
                List<Track> pickedTracks = pickTracks(allTracks, i, p);

                Playlist playlist = buildPlaylist(user, title, desc, type, pickedTracks);
                playlistRepository.save(playlist);
                totalCreated++;

                log.info("[BulkPlaylistSeeder] Seeded playlist '{}' for user {}/20 with {} track(s)",
                        title, i, pickedTracks.size());
            }
        }

        if (totalCreated == 0) {
            log.info("[BulkPlaylistSeeder] All bulk playlists already exist — skipping.");
        } else {
            log.info("[BulkPlaylistSeeder] Done. {} total playlists created.", totalCreated);
        }
    }

    private Playlist buildPlaylist(User user, String title, String description,
            PlaylistType type, List<Track> tracks) {
        String slug = SlugUtility.generateUniqueSlug(title, playlistRepository::existsBySlug);

        int totalDuration = tracks.stream().mapToInt(Track::getDurationSeconds).sum();
        List<String> genres = tracks.stream()
                .map(Track::getGenre)
                .filter(g -> g != null && !g.isBlank())
                .distinct()
                .collect(java.util.stream.Collectors.toList());

        return Playlist.builder()
                .title(title)
                .description(description)
                .type(type)
                .isPrivate(false)
                .slug(slug)
                .user(user)
                .tracks(new ArrayList<>(tracks))
                .trackCount(tracks.size())
                .totalDurationSeconds(totalDuration)
                .genres(genres)
                .likeCount(0)
                .repostCount(0)
                .build();
    }

    /**
     * Picks 2–4 tracks from allTracks using the user and playlist index as an
     * offset, so different users/playlists get different (but deterministic)
     * sets.
     */
    private List<Track> pickTracks(List<Track> allTracks, int userIndex, int playlistIndex) {
        int offset = (userIndex * 3 + playlistIndex * 2) % allTracks.size();
        int count = 2 + (userIndex + playlistIndex) % 3; // 2, 3, or 4 tracks
        count = Math.min(count, allTracks.size());

        List<Track> picked = new ArrayList<>();
        for (int t = 0; t < count; t++) {
            picked.add(allTracks.get((offset + t) % allTracks.size()));
        }
        return picked;
    }
}
