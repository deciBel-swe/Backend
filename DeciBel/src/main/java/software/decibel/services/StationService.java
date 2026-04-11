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

@Service
@RequiredArgsConstructor
public class StationService {

  private final TrackRepository trackRepository;
  private final TrackLikeRepository trackLikeRepository;
  private final TrackRepostRepository trackRepostRepository;
  private final TrackTokenRepository trackTokenRepository;
  private final StationMapper stationMapper;

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
}
