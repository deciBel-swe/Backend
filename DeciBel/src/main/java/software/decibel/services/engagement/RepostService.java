package software.decibel.services.engagement;

import java.util.HashSet;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.RepostResponse;
import software.decibel.entities.Repost;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.RepostMapper;
import software.decibel.repositories.RepostRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;

@Service
@RequiredArgsConstructor
public class RepostService {

    private final RepostRepository repostRepository;
    private final TrackRepository trackRepository; // Injected directly to avoid circular dependency
    private final UserService userService;
    private final RepostMapper repostMapper;

    @Transactional
    public RepostResponse repostTrack(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        if (repostRepository.existsByUserAndTrack(user, track)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Track already reposted");
        }

        repostRepository.save(Repost.builder()
                .user(user)
                .track(track)
                .build());

        track.setRepostCount(track.getRepostCount() + 1);
        trackRepository.save(track);

        return repostMapper.toRepostResponse(true);
    }

    @Transactional
    public RepostResponse removeRepost(Long trackId) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);
        Track track = getTrackIfExistsById(trackId);

        Repost repost = repostRepository.findByUserAndTrack(user, track)
                .orElseThrow(() -> new ResourceNotFoundException("Repost not found for track with id " + trackId));

        repostRepository.delete(repost);

        if (track.getRepostCount() > 0) {
            track.setRepostCount(track.getRepostCount() - 1);
            trackRepository.save(track);
        }

        return repostMapper.toRepostResponse(false);
    }

    public Set<Long> getRepostedTrackIds(Long userId) {
        return new HashSet<>(repostRepository.findTrackIdsByUserId(userId));
    }

    private Track getTrackIfExistsById(Long trackId) {
        return trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
    }
}
