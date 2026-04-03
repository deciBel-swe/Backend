package software.decibel.services.track;

import jakarta.transaction.Transactional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.decibel.dtos.track.TrackResponse;
import software.decibel.dtos.track.TrackTokenResponse;
import software.decibel.entities.Track;
import software.decibel.entities.TrackToken;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.UnauthorizedActionException;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.TrackTokenMapper;
import software.decibel.repositories.LikeRepository;
import software.decibel.repositories.RepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.JwtService;

@Service
@RequiredArgsConstructor
public class TrackTokenService {

    private final TrackTokenRepository trackTokenRepository;
  private final LikeRepository likeRepository;
  private final RepostRepository repostRepository;
    private final TrackService trackService;
    private final TrackTokenMapper trackTokenMapper;
    private final TrackMapper trackMapper;




    public TrackTokenResponse getActiveToken(Long trackId) {

        // To check / throw error if track doesn't exist
        trackService.getTrackIfExistsById(trackId);

        TrackToken token
                = trackTokenRepository
                        .findByTrackIdAndIsDeletedFalse(trackId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("No active token for track " + trackId));
        return trackTokenMapper.toTrackTokenResponse(token);
    }

    @Transactional
    public TrackTokenResponse regenerateToken(Long trackId) {
        Track track = trackService.getTrackIfExistsById(trackId);

    // Check user trying to regenerate token is the uploader
    if (!track.getUploader().getId().equals(JwtService.getCurrentUserId())) {
      throw new UnauthorizedActionException("You are not allowed to modify this track.");
    }

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

    @Transactional
    public TrackResponse getTrackBySecretToken(String token) {
        TrackToken trackToken = trackTokenRepository
                .findByTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired track token"));

    Long userId = JwtService.getCurrentUserId();
    Track track = trackToken.getTrack();

    boolean isLiked = likeRepository.existsByUserIdAndTrackId(userId, track.getId());
    boolean isReposted = repostRepository.existsByUserIdAndTrackId(userId, track.getId());

    return trackMapper.toTrackResponse(track, isLiked, isReposted);
    }
}
