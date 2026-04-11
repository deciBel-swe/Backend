package software.decibel.services;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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

  // TODO -> MAKE ONE FUNCTION

  // Generate a station for the current user based on the provided genre
  public StationPageResponse getGenreStation(String genre, int page, int size) {
    Long userId = JwtService.getCurrentUserId();

    Pageable pageable = PageRequest.of(page, size);

    Page<Track> tracks = trackRepository.findGenreStation(genre, userId, pageable);

    if (tracks.isEmpty()) {
      throw new NoStationResultsException();
    }

    // to set isLiked and isReposted in the tracks dto
    Set<Long> likedIds = trackLikeRepository.findTrackIdsByUserId(userId);
    Set<Long> repostedIds = trackRepostRepository.findTrackIdsByUserId(userId);

    // build tokenMap: trackId -> secretToken
    Set<Long> trackIds = tracks.getContent().stream().map(Track::getId).collect(Collectors.toSet());
    Map<Long, String> tokenMap = trackTokenRepository.findActiveTokensByTrackIds(trackIds);

    return stationMapper.toPageResponse(tracks, likedIds, repostedIds, tokenMap);
  }

  // Generate a station for the current user based on artists similar to the artists the user
  // currently follows
  // user's followed artists -> the genre of their tracks -> other tracks with these genres

  public StationPageResponse getArtistStation(Long artistId, int page, int size) {
    Long userId = JwtService.getCurrentUserId();

    // validate artist exists
    userService.getUserIfExistsById(artistId);

    PageRequest pageable = PageRequest.of(page, size);
    Page<Track> tracks = trackRepository.findArtistStation(artistId, userId, pageable);

    if (tracks.isEmpty()) {
      throw new NoStationResultsException();
    }

    Set<Long> trackIds = tracks.getContent().stream().map(Track::getId).collect(Collectors.toSet());

    Set<Long> likedIds = trackLikeRepository.findTrackIdsByUserId(userId);
    Set<Long> repostedIds = trackRepostRepository.findTrackIdsByUserId(userId);
    Map<Long, String> tokenMap = trackTokenRepository.findActiveTokensByTrackIds(trackIds);

    return stationMapper.toPageResponse(tracks, likedIds, repostedIds, tokenMap);
  }
}
