package software.decibel.services;

import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.decibel.dtos.track.TrackTokenResponse;
import software.decibel.entities.Track;
import software.decibel.entities.TrackToken;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.TrackTokenMapper;
import software.decibel.repositories.TrackTokenRepository;

@Service
@RequiredArgsConstructor
public class TrackTokenService {

  private final TrackTokenRepository trackTokenRepository;
  private final TrackService trackService;
  private final TrackTokenMapper trackTokenMapper;

  public TrackTokenResponse getActiveToken(Long trackId) {

    // To check / throw error if track doesn't exist
    trackService.getTrackById(trackId);

    TrackToken token =
        trackTokenRepository
            .findByTrackIdAndIsDeletedFalse(trackId)
            .orElseThrow(
                () -> new ResourceNotFoundException("No active token for track " + trackId));
    return trackTokenMapper.toTrackTokenResponse(token);
  }

  @Transactional
  public TrackTokenResponse regenerateToken(Long trackId) {
    Track track = trackService.getTrackById(trackId);

    // soft delete all other tokens
    trackTokenRepository
        .findByTrackIdAndIsDeletedFalse(trackId)
        .ifPresent(
            t -> {
              t.setDeleted(true);
              trackTokenRepository.save(t);
            });

    // create new token
    String tokenString = UUID.randomUUID().toString();
    TrackToken newToken = TrackToken.builder().track(track).token(tokenString).build();

    return trackTokenMapper.toTrackTokenResponse(trackTokenRepository.save(newToken));
  }
}
