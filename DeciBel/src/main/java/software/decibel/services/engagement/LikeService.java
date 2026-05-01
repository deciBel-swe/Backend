package software.decibel.services.engagement;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistSummaryResponse;
import software.decibel.dtos.track.responses.LikeResponse;
import software.decibel.dtos.user.UserProfile;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistLike;
import software.decibel.entities.Track;
import software.decibel.entities.TrackLike;
import software.decibel.entities.User;
import software.decibel.enums.AccountTier;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.LikeMapper;
import software.decibel.mappers.PlaylistMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.PlaylistLikeRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.PlaylistTokenRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.BlockService;
import software.decibel.services.JwtService;
import software.decibel.services.notification.InAppNotificationService;
import software.decibel.services.playlist.PlaylistTokenService;
import software.decibel.services.user.UserService;
import software.decibel.utils.UserMappingUtility;

@Service
@RequiredArgsConstructor
public class LikeService {

    //  Dependencies
    private final TrackLikeRepository trackLikeRepository;
    private final TrackRepostRepository trackRepostRepository;
    private final TrackRepository trackRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final InAppNotificationService inAppNotificationService;
    private final LikeMapper likeMapper;
    private final UserMapper userMapper;
    private final FollowRepository followRepository;

    private final PlaylistLikeRepository playlistLikeRepository;
    private final PlaylistRepostRepository playlistRepostRepository;
    private final PlaylistRepository playlistRepository;
    private final PlaylistTokenRepository playlistTokenRepository;
    private final PlaylistMapper playlistMapper;
    private final UserMappingUtility userMappingUtility;
    private final PlaylistTokenService playlistTokenService;

    // track like methods
    @Transactional
    public LikeResponse likeTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        if (trackLikeRepository.existsByUserAndTrack(user, track)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Track already liked");
        }

        trackLikeRepository.save(TrackLike.builder()
                .user(user)
                .track(track)
                .build());

        track.setLikeCount(track.getLikeCount() + 1);
        trackRepository.save(track);
        if (track.getUploader() != null) {
            inAppNotificationService.createNotification(
                    track.getUploader().getId(), // Recipient (Track Owner)
                    userId, // Actor (User who liked)
                    NotificationType.LIKE,
                    ResourceType.TRACK,
                    track.getId() // Resource ID
            );
        }

        return likeMapper.toLikeResponse(true);
    }

    @Transactional
    public LikeResponse unlikeTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        TrackLike like = trackLikeRepository.findByUserAndTrack(user, track)
                .orElseThrow(() -> new ResourceNotFoundException("Like not found for track with id " + trackId));

        trackLikeRepository.delete(like);

        if (track.getLikeCount() > 0) {
            track.setLikeCount(track.getLikeCount() - 1);
            trackRepository.save(track);
        }

        return likeMapper.toLikeResponse(false);
    }

    public Set<Long> getLikedTrackIds(Long userId) {
        return new HashSet<>(trackLikeRepository.findTrackIdsByUserId(userId));
    }

    private Track getTrackIfExistsById(Long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
    }

    // playlist like methods
    @Transactional
    public LikeResponse likePlaylist(Long userId, Long playlistId) {
        User user = findUser(userId);
        Playlist playlist = findPlaylist(playlistId);

        if (playlistLikeRepository.existsByUserAndPlaylist(user, playlist)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Playlist already liked");
        }

        playlistLikeRepository.save(PlaylistLike.builder()
                .user(user)
                .playlist(playlist)
                .build());

        playlist.setLikeCount(playlist.getLikeCount() + 1);
        playlistRepository.save(playlist);
        // Notify the owner of the playlist that someone liked it
        if (playlist.getUser() != null) {
            inAppNotificationService.createNotification(
                    playlist.getUser().getId(), // Recipient (Playlist Owner)
                    userId, // Actor (User who liked)
                    NotificationType.LIKE,
                    ResourceType.PLAYLIST,
                    playlist.getId() // Resource ID
            );
        }
        return likeMapper.toLikeResponse(true);
    }

    @Transactional
    public void unlikePlaylist(Long userId, Long playlistId) {
        User user = findUser(userId);
        Playlist playlist = findPlaylist(playlistId);

        PlaylistLike like = playlistLikeRepository.findByUserAndPlaylist(user, playlist)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not liked"));

        playlistLikeRepository.delete(like);
        playlist.setLikeCount(Math.max(0, playlist.getLikeCount() - 1));
        playlistRepository.save(playlist);
    }

    public Page<PlaylistSummaryResponse> getLikedPlaylists(String username, Pageable pageable) {
        // 1. Get the target user
        User user = userService.getUserIfExistsByUsername(username);

        // 2. Fetch the page of liked playlists
        Page<Playlist> likedPlaylists = playlistLikeRepository.findPlaylistsByUserId(user.getId(), pageable);

        // 3. Get the CURRENT logged-in user context
        Long currentUserId = JwtService.getCurrentUserId();
        User currentUser = currentUserId != null ? userService.getUserIfExistsById(currentUserId) : null;
        AccountTier tier = currentUser != null ? currentUser.getTier() : AccountTier.FREE;

        // 4. Map EACH playlist individually and attach the token for EVERYONE
        return likedPlaylists.map(playlist -> {

            // Fetch the token universally, no matter who is looking
            String token = playlistTokenService.resolveSecretToken(playlist);

            if (currentUserId == null) {
                return playlistMapper.toSummaryResponse(playlist, token);
            }

            // Return the fully mapped DTO with the universally fetched token
            return playlistMapper.toSummaryResponse(
                    playlist,
                    trackLikeRepository.findTrackIdsByUserId(currentUserId),
                    trackRepostRepository.findTrackIdsByUserId(currentUserId),
                    true, // We know it's true because it's the liked playlists endpoint
                    playlistRepostRepository.existsByUserAndPlaylist(currentUser, playlist),
                    tier,
                    token // <-- Pass the token here
            );
        });
    }

    private User findUser(Long userId) {
        return userService.getUserIfExistsById(userId);
    }

    private Playlist findPlaylist(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist with id " + playlistId + " not found"));
    }

    // used to get the users who liked a track, for the GET /tracks/{trackId}/likes endpoint
    public Page<UserProfile> getTrackLikers(Long trackId, Pageable pageable) {
        trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
        User currentViewer = resolveCurrentViewer();
        return trackLikeRepository
                .findUsersByTrackId(trackId, pageable)
                .map(u -> userMapper.toUserProfile(u, currentViewer, userMappingUtility, followRepository, blockService));
    }

    // used to get the users who liked a playlist, for the GET /playlists/{playlistId}/likes endpoint
    public Page<UserProfile> getPlaylistLikers(Long playlistId, Pageable pageable) {
        playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist with id " + playlistId + " not found"));
        User currentViewer = resolveCurrentViewer();
        return playlistLikeRepository
                .findUsersByPlaylistId(playlistId, pageable)
                .map(u -> userMapper.toUserProfile(u, currentViewer, userMappingUtility, followRepository, blockService));
    }

    // Resolves the currently authenticated user, or null for anonymous requests
    private User resolveCurrentViewer() {
        try {
            Long currentUserId = JwtService.getCurrentUserId();
            return userService.getUserIfExistsById(currentUserId);
        } catch (Exception e) {
            return null;
        }
    }

    public Set<Long> getLikedPlaylistIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return playlistLikeRepository.findPlaylistIdsByUserId(userId);
    }
}
