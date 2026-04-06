package software.decibel.services.user;

import java.util.HashSet;
import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.entities.Track;
import software.decibel.mappers.TrackMapper;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.services.JwtService;

@Service
@RequiredArgsConstructor
public class UserRepostService {

    private final TrackLikeRepository likeRepository;
    private final TrackRepostRepository repostRepository;

    private final TrackMapper trackMapper;

    // Get all tracks reposted by user
    @Transactional
    public TrackPageResponse getRepostedTracks(int page, int size) {
        Long userId = JwtService.getCurrentUserId();

        PageRequest pageable = PageRequest.of(page, size);
        Page<Track> result = repostRepository.findRepostedTracksByUserId(userId, pageable);

        Set<Long> likedTrackIds = new HashSet<>(likeRepository.findTrackIdsByUserId(userId));
        Set<Long> repostedTrackIds = new HashSet<>(repostRepository.findTrackIdsByUserId(userId));

        return trackMapper.toPageResponse(result, likedTrackIds, repostedTrackIds);
    }
}
