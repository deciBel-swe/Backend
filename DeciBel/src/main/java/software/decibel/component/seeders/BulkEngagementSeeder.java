package software.decibel.component.seeders;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.entities.AuthIdentity;
import software.decibel.entities.Comment;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistLike;
import software.decibel.entities.PlaylistRepost;
import software.decibel.entities.Track;
import software.decibel.entities.TrackLike;
import software.decibel.entities.TrackRepost;
import software.decibel.entities.User;
import software.decibel.enums.AuthProvider;
import software.decibel.enums.AuthType;
import software.decibel.repositories.AuthIdentityRepository;
import software.decibel.repositories.CommentRepository;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;

@Component
@Order(9)
@Profile("dev")
@RequiredArgsConstructor
@Slf4j
public class BulkEngagementSeeder implements ApplicationListener<ApplicationReadyEvent> {

    private final AuthIdentityRepository authIdentityRepository;
    private final TrackRepository trackRepository;
    private final PlaylistRepository playlistRepository;
    private final TrackLikeRepository trackLikeRepository;
    private final TrackRepostRepository trackRepostRepository;
    private final PlaylistLikeRepository playlistLikeRepository;
    private final PlaylistRepostRepository playlistRepostRepository;
    private final CommentRepository commentRepository;

    private static final String[] COMMENTS = {
        "This track is fire!",
        "Been on repeat all day.",
        "The production on this is insane.",
        "Absolute vibe, love it.",
        "This hits different at night.",
        "Can't stop listening to this.",
        "The bassline is everything.",
        "This is exactly what I needed today.",
        "Underrated track, more people need to hear this.",
        "The drop caught me off guard, amazing.",};

    @Override
    @Transactional
    public void onApplicationEvent(ApplicationReadyEvent event) {
        // Skip if engagement already exists
        if (trackLikeRepository.count() > 0) {
            log.info("[BulkEngagementSeeder] Engagement already exists — skipping.");
            return;
        }

        List<User> users = loadAllBulkUsers();
        List<Track> tracks = trackRepository.findAll();
        List<Playlist> playlists = playlistRepository.findAll();

        if (users.isEmpty()) {
            log.warn("[BulkEngagementSeeder] No bulk users found — skipping.");
            return;
        }
        if (tracks.isEmpty()) {
            log.warn("[BulkEngagementSeeder] No tracks found — skipping.");
            return;
        }

        int trackLikes = 0, trackReposts = 0;
        int playlistLikes = 0, playlistReposts = 0;
        int comments = 0;

        for (int i = 0; i < users.size(); i++) {
            User user = users.get(i);

            // ── Track Likes ───────────────────────────────────────────────
            // Each user likes ~3 tracks, offset by user index so they like different ones
            for (int t = 0; t < 3; t++) {
                Track track = tracks.get((i * 3 + t) % tracks.size());
                if (!trackLikeRepository.existsByUserAndTrack(user, track)) {
                    trackLikeRepository.save(TrackLike.builder()
                            .user(user)
                            .track(track)
                            .build());
                    track.setLikeCount(track.getLikeCount() + 1);
                    trackRepository.save(track);
                    trackLikes++;
                }
            }

            // ── Track Reposts ─────────────────────────────────────────────
            // Each user reposts ~2 tracks, different offset from likes
            for (int t = 0; t < 2; t++) {
                Track track = tracks.get((i * 2 + t + 1) % tracks.size());
                if (!trackRepostRepository.existsByUserAndTrack(user, track)) {
                    trackRepostRepository.save(TrackRepost.builder()
                            .user(user)
                            .track(track)
                            .build());
                    track.setRepostCount(track.getRepostCount() + 1);
                    trackRepository.save(track);
                    trackReposts++;
                }
            }

            // ── Track Comments ────────────────────────────────────────────
            // Each user comments on 2 tracks
            for (int t = 0; t < 2; t++) {
                Track track = tracks.get((i + t * 3) % tracks.size());
                String content = COMMENTS[(i + t) % COMMENTS.length];
                Comment comment = Comment.builder()
                        .user(user)
                        .track(track)
                        .content(content)
                        .timestampSeconds(null)
                        .build();
                commentRepository.save(comment);
                track.setCommentCount(track.getCommentCount() + 1);
                trackRepository.save(track);
                comments++;
            }

            // ── Playlist Likes ────────────────────────────────────────────
            if (!playlists.isEmpty()) {
                for (int p = 0; p < Math.min(2, playlists.size()); p++) {
                    Playlist playlist = playlists.get((i + p) % playlists.size());
                    if (!playlistLikeRepository.existsByUserAndPlaylist(user, playlist)) {
                        playlistLikeRepository.save(PlaylistLike.builder()
                                .user(user)
                                .playlist(playlist)
                                .build());
                        playlist.setLikeCount(playlist.getLikeCount() + 1);
                        playlistRepository.save(playlist);
                        playlistLikes++;
                    }
                }
            }

            // ── Playlist Reposts ──────────────────────────────────────────
            if (!playlists.isEmpty()) {
                Playlist playlist = playlists.get((i * 2 + 1) % playlists.size());
                if (!playlistRepostRepository.existsByUserAndPlaylist(user, playlist)) {
                    playlistRepostRepository.save(PlaylistRepost.builder()
                            .user(user)
                            .playlist(playlist)
                            .build());
                    playlist.setRepostCount(playlist.getRepostCount() + 1);
                    playlistRepository.save(playlist);
                    playlistReposts++;
                }
            }

            log.info("[BulkEngagementSeeder] Seeded engagement for user {}/{}",
                    i + 1, users.size());
        }

        log.info("[BulkEngagementSeeder] Done. Track likes: {}, Track reposts: {}, "
                + "Comments: {}, Playlist likes: {}, Playlist reposts: {}",
                trackLikes, trackReposts, comments, playlistLikes, playlistReposts);
    }

    /**
     * Loads all 20 bulk users via their AuthIdentity so we get the managed User
     * entity — same pattern used in BulkTrackSeeder and BulkPlaylistSeeder.
     */
    private List<User> loadAllBulkUsers() {
        return java.util.stream.IntStream.rangeClosed(1, 20)
                .mapToObj(i -> "bulk.user" + i + "@decibel.dev")
                .map(email -> authIdentityRepository
                .findByEmailIgnoreCaseAndProviderAndType(
                        email, AuthProvider.LOCAL, AuthType.PASSWORD)
                .map(AuthIdentity::getUser)
                .orElse(null))
                .filter(u -> u != null)
                .collect(java.util.stream.Collectors.toList());
    }
}
