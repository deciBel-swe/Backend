package software.decibel.services.user;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.entities.Track;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.UserRepository;
import software.decibel.services.JwtService;

@Service
@RequiredArgsConstructor
public class UserLikeService {

    private final TrackRepository trackRepository;
    private final TrackLikeRepository likeRepository;
    private final TrackRepostRepository repostRepository;
    private final UserRepository userRepository;

    private final TrackMapper trackMapper;

    // Get all tracks liked by user
    @Transactional
    public TrackPageResponse getLikedTracks(int page, int size) {
        Long userId = JwtService.getCurrentUserId();

        PageRequest pageable = PageRequest.of(page, size);
        Page<Track> result = likeRepository.findLikedTracksByUserId(userId, pageable);

        Set<Long> likedTrackIds = new HashSet<>(likeRepository.findTrackIdsByUserId(userId));
        Set<Long> repostedTrackIds = new HashSet<>(repostRepository.findTrackIdsByUserId(userId));

        return trackMapper.toPageResponse(result, likedTrackIds, repostedTrackIds);
    }
    // Get all tracks liked by user

    // Get all tracks liked by a specific user (by username)
    @Transactional(readOnly = true)
    public TrackPageResponse getLikedTracksByUsername(String username, int page, int size) {
        // 1. Get the target user whose profile we are viewing
        Long targetUserId = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username))
                .getId();

        // 2. Fetch the tracks THEY liked
        PageRequest pageable = PageRequest.of(page, size);
        Page<Track> result = likeRepository.findLikedTracksByUserId(targetUserId, pageable);

        // 3. Get the CURRENT logged-in user's state for UI flags (isLiked / isReposted)
        Long currentUserId = JwtService.getCurrentUserId();

        Set<Long> likedTrackIds = new HashSet<>();
        Set<Long> repostedTrackIds = new HashSet<>();

        // If the viewer is logged in, fetch their specific likes/reposts.
        // If they are a guest, the sets remain empty (all UI icons will show as false).
        if (currentUserId != null) {
            likedTrackIds.addAll(likeRepository.findTrackIdsByUserId(currentUserId));
            repostedTrackIds.addAll(repostRepository.findTrackIdsByUserId(currentUserId));
        }
        return trackMapper.toPageResponse(result, likedTrackIds, repostedTrackIds);
    }

    // Get all tracks reposted by user
    // Get all tracks reposted by a specific user (by username)
    @Transactional(readOnly = true)
    public TrackPageResponse getRepostedTracksByUsername(String username, int page, int size) {
        // 1. Get the target user whose profile we are viewing
        Long targetUserId = userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found with username: " + username))
                .getId();

        // 2. Fetch the tracks THEY reposted
        PageRequest pageable = PageRequest.of(page, size);
        Page<Track> result = repostRepository.findRepostedTracksByUserId(targetUserId, pageable);

        // 3. Get the CURRENT logged-in user's state for UI flags
        Long currentUserId = JwtService.getCurrentUserId();

        Set<Long> likedTrackIds = new HashSet<>();
        Set<Long> repostedTrackIds = new HashSet<>();

        if (currentUserId != null) {
            likedTrackIds.addAll(likeRepository.findTrackIdsByUserId(currentUserId));
            repostedTrackIds.addAll(repostRepository.findTrackIdsByUserId(currentUserId));
        }

        return trackMapper.toPageResponse(result, likedTrackIds, repostedTrackIds);
    }
}
