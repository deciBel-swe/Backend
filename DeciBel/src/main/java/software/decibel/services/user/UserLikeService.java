package software.decibel.services.user;

import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.dtos.track.responses.TrackPageResponse;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.BlockRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.JwtService;

@Service
@RequiredArgsConstructor
public class UserLikeService {

    private final TrackRepository trackRepository;
    private final TrackLikeRepository likeRepository;
    private final TrackRepostRepository repostRepository;
    private final TrackMapper trackMapper;
    private final UserService userService;
    private final BlockRepository blockRepository;

    // Get all tracks liked by user
    @Transactional
    public TrackPageResponse getLikedTracks(int page, int size) {
        Long userId = JwtService.getCurrentUserId();
        userService.getUserIfExistsById(userId);

        PageRequest pageable = PageRequest.of(page, size);
        Page<Track> result = likeRepository.findLikedTracksByUserId(userId, pageable);

        Set<Long> likedTrackIds = new HashSet<>(likeRepository.findTrackIdsByUserId(userId));
        Set<Long> repostedTrackIds = new HashSet<>(repostRepository.findTrackIdsByUserId(userId));

        return trackMapper.toPageResponse(
                result,
                userService.getUserIfExistsById(JwtService.getCurrentUserId()).getTier(),
                likedTrackIds,
                repostedTrackIds);
    }
    // Get all tracks liked by user

    // Get all tracks liked by a specific user (by username)
    @Transactional(readOnly = true)
    public TrackPageResponse getLikedTracksByUsername(String username, int page, int size) {
        Long currentUserId = JwtService.getCurrentUserId();
        User targetUser = userService.getUserIfExistsByUsername(username);

        // Check if user has been blocked
        if (currentUserId != null && !currentUserId.equals(targetUser.getId())) {
            boolean isBlocked = blockRepository.existsByBlocker_IdAndBlocked_Id(currentUserId, targetUser.getId()) ||
                               blockRepository.existsByBlocker_IdAndBlocked_Id(targetUser.getId(), currentUserId);

            if (isBlocked) {
                throw new software.decibel.exceptions.custom.ResourceNotFoundException("User not found: " + username);
            }
        }

        // 2. Fetch the tracks THEY liked
        PageRequest pageable = PageRequest.of(page, size);
        Page<Track> result = likeRepository.findLikedTracksByUserId(targetUser.getId(), pageable);

        Set<Long> likedTrackIds = new HashSet<>();
        Set<Long> repostedTrackIds = new HashSet<>();

        // If the viewer is logged in, fetch their specific likes/reposts.
        // If they are a guest, the sets remain empty (all UI icons will show as false).
        if (currentUserId != null) {
            likedTrackIds.addAll(likeRepository.findTrackIdsByUserId(currentUserId));
            repostedTrackIds.addAll(repostRepository.findTrackIdsByUserId(currentUserId));
        }
        return trackMapper.toPageResponse(
                result,
                userService.getUserIfExistsById(JwtService.getCurrentUserId()).getTier(),
                likedTrackIds,
                repostedTrackIds);
    }

    // Get all tracks reposted by user
    // Get all tracks reposted by a specific user (by username)
    @Transactional(readOnly = true)
    public TrackPageResponse getRepostedTracksByUsername(String username, int page, int size) {
        Long currentUserId = JwtService.getCurrentUserId();
        User targetUser = userService.getUserIfExistsByUsername(username);

        // Check if user has been blocked
        if (currentUserId != null && !currentUserId.equals(targetUser.getId())) {
            boolean isBlocked = blockRepository.existsByBlocker_IdAndBlocked_Id(currentUserId, targetUser.getId()) ||
                               blockRepository.existsByBlocker_IdAndBlocked_Id(targetUser.getId(), currentUserId);

            if (isBlocked) {
                throw new software.decibel.exceptions.custom.ResourceNotFoundException("User not found: " + username);
            }
        }

        // 2. Fetch the tracks THEY reposted
        PageRequest pageable = PageRequest.of(page, size);
        Page<Track> result = repostRepository.findRepostedTracksByUserId(targetUser.getId(), pageable);

        Set<Long> likedTrackIds = new HashSet<>();
        Set<Long> repostedTrackIds = new HashSet<>();

        if (currentUserId != null) {
            likedTrackIds.addAll(likeRepository.findTrackIdsByUserId(currentUserId));
            repostedTrackIds.addAll(repostRepository.findTrackIdsByUserId(currentUserId));
        }

        return trackMapper.toPageResponse(
                result,
                userService.getUserIfExistsById(JwtService.getCurrentUserId()).getTier(),
                likedTrackIds,
                repostedTrackIds);
    }
}
