package software.decibel.services.engagement;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.engagement.RepostItemResponse;
import software.decibel.dtos.track.responses.RepostResponse;
import software.decibel.dtos.user.UserProfile;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistRepost;
import software.decibel.entities.Track;
import software.decibel.entities.TrackRepost;
import software.decibel.entities.User;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.enums.Visibility;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.RepostMapper;
import software.decibel.mappers.UserMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.PlaylistRepository;
import software.decibel.repositories.PlaylistRepostRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.BlockService;
import software.decibel.services.JwtService;
import software.decibel.services.notification.InAppNotificationService;
import software.decibel.services.user.UserService;
import software.decibel.utils.UserMappingUtility;

@Service
@RequiredArgsConstructor
public class RepostService {

    // Repost Dependencies
    private final TrackRepostRepository trackRepostRepository;
    private final TrackRepository trackRepository;
    private final UserService userService;
    private final BlockService blockService;
    private final InAppNotificationService inAppNotificationService;
    private final RepostMapper repostMapper;
    private final PlaylistRepostRepository playlistRepostRepository;
    private final PlaylistRepository playlistRepository;
    private final UserMappingUtility userMappingUtility;
    private final FollowRepository followRepository;
    private final UserMapper userMapper;

    @Transactional
    public RepostResponse repostTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        if (trackRepostRepository.existsByUserAndTrack(user, track)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Track already reposted");
        }
        if (track.getVisibility() == Visibility.PRIVATE) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cannot repost a private track");
        }

        trackRepostRepository.save(TrackRepost.builder()
                .user(user)
                .track(track)
                .build());

        track.setRepostCount(track.getRepostCount() + 1);
        trackRepository.save(track);
        // Notify the owner of the track that someone reposted it
        if (track.getUploader() != null) {
            inAppNotificationService.createNotification(
                    track.getUploader().getId(), // Recipient (Track Owner)
                    userId, // Actor (User who reposted)
                    NotificationType.REPOST, // Notification type
                    ResourceType.TRACK, // Resource type
                    track.getId() // Resource ID
            );
        }

        return repostMapper.toRepostResponse(true);
    }

    @Transactional
    public RepostResponse removeRepost(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        TrackRepost repost = trackRepostRepository.findByUserAndTrack(user, track)
                .orElseThrow(() -> new ResourceNotFoundException("Repost not found for track with id " + trackId));

        trackRepostRepository.delete(repost);

        if (track.getRepostCount() > 0) {
            track.setRepostCount(track.getRepostCount() - 1);
            trackRepository.save(track);
        }

        return repostMapper.toRepostResponse(false);
    }

    public Set<Long> getRepostedTrackIds(Long userId) {
        return new HashSet<>(trackRepostRepository.findTrackIdsByUserId(userId));
    }

    private Track getTrackIfExistsById(Long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
    }

    // Playlist repost methods
    @Transactional
    public RepostResponse repostPlaylist(Long userId, Long playlistId) {
        User user = findUser(userId);
        Playlist playlist = findPlaylist(playlistId);

        if (playlistRepostRepository.existsByUserAndPlaylist(user, playlist)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Playlist already reposted");
        }

        playlistRepostRepository.save(PlaylistRepost.builder()
                .user(user)
                .playlist(playlist)
                .build());

        playlist.setRepostCount(playlist.getRepostCount() + 1);
        playlistRepository.save(playlist);
        if (playlist.getUser() != null) {
            inAppNotificationService.createNotification(
                    playlist.getUser().getId(), // Recipient (Playlist Owner)
                    userId, // Actor (User who reposted)
                    NotificationType.REPOST, // Notification type
                    ResourceType.PLAYLIST, // Resource type
                    playlist.getId() // Resource ID
            );
        }
        return ResponseEntity.ok(repostMapper.toRepostResponse(true)).getBody();
    }

    @Transactional
    public void unrepostPlaylist(Long userId, Long playlistId) {
        User user = findUser(userId);
        Playlist playlist = findPlaylist(playlistId);

        PlaylistRepost repost = playlistRepostRepository.findByUserAndPlaylist(user, playlist)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Playlist not reposted"));

        playlistRepostRepository.delete(repost);
        playlist.setRepostCount(Math.max(0, playlist.getRepostCount() - 1));
        playlistRepository.save(playlist);
    }

    private Playlist findPlaylist(Long playlistId) {
        return playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist with id " + playlistId + " not found"));
    }

    // Mixed feed of track + playlist reposts in chronological order
    public Page<RepostItemResponse> getUserReposts(String username, Pageable pageable) {
        Long currentUserId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsByUsername(username);

        if (blockService.isBlockRelationshipActive(currentUserId, user.getId())) {
            throw new ResourceNotFoundException("User not found: " + username);
        }

        List<RepostItemResponse> all = new ArrayList<>();

        // Add playlist reposts
        playlistRepostRepository.findByUser(user, Pageable.unpaged())
                .forEach(r -> all.add(new RepostItemResponse(
                "PLAYLIST",
                r.getPlaylist().getId(),
                r.getPlaylist().getTitle(),
                r.getPlaylist().getCoverArtUrl(),
                r.getRepostedAt()
        )));

        // Add track reposts
        trackRepostRepository.findByUser(user, Pageable.unpaged())
                .forEach(r -> all.add(new RepostItemResponse(
                "TRACK",
                r.getTrack().getId(),
                r.getTrack().getTitle(),
                r.getTrack().getCoverUrl(),
                r.getRepostedAt()
        )));

        // Sort by repostedAt descending
        all.sort(Comparator.comparing(RepostItemResponse::repostedAt).reversed());

        // Manual pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), all.size());
        List<RepostItemResponse> page = start >= all.size() ? List.of() : all.subList(start, end);

        return new PageImpl<>(page, pageable, all.size());
    }

    // used for getting all track reposters
    public Page<UserProfile> getTrackReposters(Long trackId, Pageable pageable) {
        trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
        User currentViewer = resolveCurrentViewer();
        return trackRepostRepository
                .findUsersByTrackId(trackId, pageable)
                .map(u -> userMapper.toUserProfile(u, currentViewer, userMappingUtility, followRepository, blockService));
    }

    // used for getting all playlist reposters
    public Page<UserProfile> getPlaylistReposters(Long playlistId, Pageable pageable) {
        playlistRepository.findById(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException("Playlist with id " + playlistId + " not found"));
        User currentViewer = resolveCurrentViewer();
        return playlistRepostRepository
                .findUsersByPlaylistId(playlistId, pageable)
                .map(u -> userMapper.toUserProfile(u, currentViewer, userMappingUtility, followRepository, blockService));
    }

    private User findUser(Long userId) {
        return userService.getUserIfExistsById(userId);
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

    public Set<Long> getRepostedPlaylistIds(Long userId) {
        if (userId == null) {
            return Collections.emptySet();
        }
        return playlistRepostRepository.findPlaylistIdsByUserId(userId);
    }

}
