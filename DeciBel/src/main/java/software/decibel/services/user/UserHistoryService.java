package software.decibel.services.user;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.entities.ListeningHistory;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.mappers.StationMapper;
import software.decibel.mappers.TrackMapper;
import software.decibel.projections.TrackTokenProjection;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.ListeningHistoryRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.JwtService;

@Service
@RequiredArgsConstructor
public class UserHistoryService {

    private final ListeningHistoryRepository listeningHistoryRepository;
    private final TrackLikeRepository trackLikeRepository;
    private final TrackRepostRepository trackRepostRepository;
    private final TrackTokenRepository trackTokenRepository;
    private final FollowRepository followRepository;
    private final StationMapper stationMapper;
    private final TrackMapper trackMapper;
    private final UserService userService;

    @Transactional(readOnly = true)
    public StationPageResponse getMyListeningHistory(int page, int size) {
        Long userId = JwtService.getCurrentUserId();
        User user = userService.getUserIfExistsById(userId);

        Page<ListeningHistory> historyPage = listeningHistoryRepository.findByUserIdOrderByPlayedAtDesc(
                userId,
                PageRequest.of(page, size));

        Page<Track> tracks = historyPage.map(ListeningHistory::getTrack);
        Set<Long> trackIds = tracks.getContent().stream().map(Track::getId).collect(Collectors.toSet());
        Set<Long> likedIds = trackLikeRepository.findTrackIdsByUserId(userId);
        Set<Long> repostedIds = trackRepostRepository.findTrackIdsByUserId(userId);
        Set<Long> followingArtistIds = Set.copyOf(followRepository.findFollowingIdsByFollowerId(userId));
        Map<Long, String> tokenMap;
        if (trackIds.isEmpty()) {
            tokenMap = Map.of();
        } else {
            // Fetch the lightweight projections from the database
            var projections = trackTokenRepository.findActiveTokensByTrackIds(trackIds);

            //Convert the projections into aJava Map<Long, String>
            tokenMap = projections.stream()
                    .collect(Collectors.toMap(
                            TrackTokenProjection::getTrackId,
                            TrackTokenProjection::getToken
                    ));
        }
        return stationMapper.toPageResponse(
                tracks, likedIds, repostedIds, tokenMap, followingArtistIds, user.getTier(), trackMapper);
    }
}
