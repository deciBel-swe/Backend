package software.decibel.services;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.entities.Track;
import software.decibel.exceptions.custom.NoStationResultsException;
import software.decibel.mappers.StationMapper;
import software.decibel.repositories.FollowRepository;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.user.UserService;

@Service
@RequiredArgsConstructor
public class StationService {

    private static final int MAX_STATION_TRACKS = 50;

    private final TrackRepository trackRepository;
    private final TrackLikeRepository trackLikeRepository;
    private final TrackRepostRepository trackRepostRepository;
    private final TrackTokenRepository trackTokenRepository;
    private final FollowRepository followRepository;
    private final StationMapper stationMapper;
    private final UserService userService;

    // Tracks with same genres as ones you liked + filtering
    public StationPageResponse getGenreStation(int page, int size) {
        Long userId = JwtService.getCurrentUserId();
        List<Track> trackList = trackRepository.findGenreStation(userId, PageRequest.of(0, 50)).getContent();
        //fallback
        if (trackList.isEmpty()) {
            trackList = trackRepository.findMostPopularTracks(PageRequest.of(0, 50)).getContent();
        }

        Page<Track> tracks = paginateList(trackList, page, size);
        return buildStationResponse(tracks, userId);
    }

    // Tracks with same genres as ones posted by artists you follow + filtering
    public StationPageResponse getArtistStation(int page, int size) {
        Long userId = JwtService.getCurrentUserId();
        List<Track> trackList = trackRepository.findArtistStation(userId, PageRequest.of(0, 50)).getContent();
        //fallback
        if (trackList.isEmpty()) {
            trackList = trackRepository.findMostPopularArtistTracks(PageRequest.of(0, 50)).getContent();
        }
        Page<Track> tracks = paginateList(trackList, page, size);
        return buildStationResponse(tracks, userId);
    }

    // Tracks with same tags as ones you liked
    public StationPageResponse getLikesStation(int page, int size) {
        Long userId = JwtService.getCurrentUserId();
        //fallback
        List<Track> trackList = trackRepository.findLikesStation(userId, PageRequest.of(0, 50)).getContent();
        if (trackList.isEmpty()) {
            trackList = trackRepository.findMostLikedTracks(PageRequest.of(0, 50)).getContent();
        }
        Page<Track> tracks = paginateList(trackList, page, size);
        return buildStationResponse(tracks, userId);
    }

    // Shared logic for all stations:
    private StationPageResponse buildStationResponse(Page<Track> tracks, Long userId) {
        // throw if tracks empty
        if (tracks.isEmpty()) {
            throw new NoStationResultsException();
        }

        // build dto (show if liked, reposted, and their tokens)
        Set<Long> trackIds = tracks.getContent().stream().map(Track::getId).collect(Collectors.toSet());

        Set<Long> likedIds = trackLikeRepository.findTrackIdsByUserId(userId);
        Set<Long> repostedIds = trackRepostRepository.findTrackIdsByUserId(userId);
        Map<Long, String> tokenMap;
        if (trackIds.isEmpty()) {
            tokenMap = Map.of();
        } else {
            var projections = trackTokenRepository.findActiveTokensByTrackIds(trackIds);
            tokenMap = projections.stream()
                    .collect(Collectors.toMap(
                            software.decibel.projections.TrackTokenProjection::getTrackId,
                            software.decibel.projections.TrackTokenProjection::getToken
                    ));
        }
        Set<Long> followingArtistIds = Set.copyOf(followRepository.findFollowingIdsByFollowerId(userId));

        return stationMapper.toPageResponse(tracks, likedIds, repostedIds, tokenMap, followingArtistIds);
    }

    //helper for pagination
    private Page<Track> paginateList(List<Track> list, int page, int size) {
        int start = Math.min(page * size, list.size());
        int end = Math.min(start + size, list.size());

        List<Track> pagedList = list.subList(start, end);

        // Convert the subList back into a Spring Page object
        return new PageImpl<>(pagedList, PageRequest.of(page, size), list.size());
    }
}
