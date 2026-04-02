package software.decibel.services.engagement;

import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.LikeResponse;
import software.decibel.entities.Like;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.LikeMapper;
import software.decibel.repositories.LikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;

@Service
@RequiredArgsConstructor
public class LikeService {

    private final LikeRepository likeRepository;
    private final TrackRepository trackRepository; // Injected directly to avoid circular dependency with TrackService
    private final UserService userService;
    private final LikeMapper likeMapper;

    @Transactional
    public LikeResponse likeTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        if (likeRepository.existsByUserAndTrack(user, track)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Track already liked");
        }

        likeRepository.save(Like.builder()
                .user(user)
                .track(track)
                .build());

        track.setLikeCount(track.getLikeCount() + 1);
        trackRepository.save(track);

        return likeMapper.toLikeResponse(true);
    }

    @Transactional
    public LikeResponse unlikeTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        Like like = likeRepository.findByUserAndTrack(user, track)
                .orElseThrow(() -> new ResourceNotFoundException("Like not found for track with id " + trackId));

        likeRepository.delete(like);

        if (track.getLikeCount() > 0) {
            track.setLikeCount(track.getLikeCount() - 1);
            trackRepository.save(track);
        }

        return likeMapper.toLikeResponse(false);
    }

    public Set<Long> getLikedTrackIds(Long userId) {
        return new HashSet<>(likeRepository.findTrackIdsByUserId(userId));
    }

    private Track getTrackIfExistsById(Long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
    }
}
