package software.decibel.services;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import software.decibel.dtos.discovery.StationPageResponse;
import software.decibel.entities.Track;
import software.decibel.exceptions.custom.NoStationResultsException;
import software.decibel.mappers.StationMapper;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.user.UserService;

@Service
@RequiredArgsConstructor
public class StationService {

  private final TrackRepository trackRepository;
  private final TrackLikeRepository trackLikeRepository;
  private final TrackRepostRepository trackRepostRepository;
  private final TrackTokenRepository trackTokenRepository;
  private final StationMapper stationMapper;
  private final UserService userService;

  public StationPageResponse getGenreStation(String genre, int page, int size) {
    Long userId = JwtService.getCurrentUserId();
    Page<Track> tracks =
        trackRepository.findGenreStation(genre, userId, PageRequest.of(page, size));
    return buildStationResponse(tracks, userId);
  }

  public StationPageResponse getArtistStation(Long artistId, int page, int size) {
    Long userId = JwtService.getCurrentUserId();
    userService.getUserIfExistsById(artistId);
    Page<Track> tracks =
        trackRepository.findArtistStation(artistId, userId, PageRequest.of(page, size));
    return buildStationResponse(tracks, userId);
  }

  public StationPageResponse getLikesStation(int page, int size) {
    Long userId = JwtService.getCurrentUserId();
    Page<Track> tracks = trackRepository.findLikesStation(userId, PageRequest.of(page, size));
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
    Map<Long, String> tokenMap = trackTokenRepository.findActiveTokensByTrackIds(trackIds);

    return stationMapper.toPageResponse(tracks, likedIds, repostedIds, tokenMap);
  }
}
