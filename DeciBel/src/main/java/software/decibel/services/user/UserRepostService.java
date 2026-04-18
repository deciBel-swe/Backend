package software.decibel.services.user;

import jakarta.transaction.Transactional;
import java.util.HashSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import software.decibel.dtos.track.responses.TrackPageResponse;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.JwtService;

@Service
@RequiredArgsConstructor
public class UserRepostService {

    private final TrackLikeRepository likeRepository;
    private final TrackRepostRepository repostRepository;
  private final UserService userService;

    private final TrackMapper trackMapper;

    // Get all tracks reposted by user
    @Transactional
    public TrackPageResponse getRepostedTracks(int page, int size) {
    Long currentUserId = JwtService.getCurrentUserId();
    User currentUser = userService.getUserIfExistsById(currentUserId);

        PageRequest pageable = PageRequest.of(page, size);
    Page<Track> result = repostRepository.findRepostedTracksByUserId(currentUserId, pageable);

    Set<Long> likedTrackIds = new HashSet<>(likeRepository.findTrackIdsByUserId(currentUserId));
    Set<Long> repostedTrackIds =
        new HashSet<>(repostRepository.findTrackIdsByUserId(currentUserId));

    return trackMapper.toPageResponse(
        result, currentUser.getTier(), likedTrackIds, repostedTrackIds);
    }
}
