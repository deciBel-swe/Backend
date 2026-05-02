package software.decibel.services.track;

import java.util.UUID;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.responses.TrackResponse;
import software.decibel.dtos.track.responses.TrackTokenResponse;
import software.decibel.entities.Track;
import software.decibel.entities.TrackToken;
import software.decibel.enums.AccountTier;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.exceptions.custom.UnauthorizedActionException;
import software.decibel.mappers.TrackMapper;
import software.decibel.mappers.TrackTokenMapper;
import software.decibel.repositories.TrackLikeRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.repositories.TrackRepostRepository;
import software.decibel.repositories.TrackTokenRepository;
import software.decibel.services.JwtService;
import software.decibel.services.user.UserService;
import software.decibel.utils.TrackChecksUtil;

@Service
@RequiredArgsConstructor
public class TrackTokenService {

    private final TrackTokenRepository trackTokenRepository;
    private final TrackLikeRepository likeRepository;
    private final TrackRepostRepository repostRepository;
    private final TrackChecksUtil trackChecksUtil;
    private final UserService userService;
    private final TrackTokenMapper trackTokenMapper;
    private final TrackMapper trackMapper;
    private final TrackRepository trackRepository;

    public TrackTokenResponse getActiveToken(Long trackId) {

        // To check / throw error if track doesn't exist
        trackChecksUtil.getTrackIfExistsById(trackId);

        TrackToken token
                = trackTokenRepository
                        .findByTrackIdAndIsDeletedFalse(trackId)
                        .orElseThrow(
                                () -> new ResourceNotFoundException("No active token for track " + trackId));
        return trackTokenMapper.toTrackTokenResponse(token);
    }

    @Transactional
    public TrackToken generateToken(Long trackId, Long userId) {
        Track track = trackRepository.findById(trackId)
                .orElseThrow(() -> new ResourceNotFoundException("Track with id " + trackId + " not found"));
        // Check user trying to regenerate token is the uploader
        if (!track.getUploader().getId().equals(userId)) {
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

        TrackToken savedToken = trackTokenRepository.save(newToken);
        return savedToken;
    }

    @Transactional
    public TrackTokenResponse regenerateToken(Long trackId) {
        Track track = trackChecksUtil.getTrackIfExistsById(trackId);

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
        track.getTokens().add(newToken);

        return trackTokenMapper.toTrackTokenResponse(trackTokenRepository.save(newToken));
    }

    @Transactional
    public TrackResponse getTrackBySecretToken(String token) {
        TrackToken trackToken = trackTokenRepository
                .findByTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired track token"));
        Track track = trackToken.getTrack();
        Long userId = null;
        boolean isLiked = false;
        boolean isReposted = false;
        AccountTier tier = AccountTier.FREE;
        try {
            userId = JwtService.getCurrentUserId();
            isLiked = likeRepository.existsByUserIdAndTrackId(userId, track.getId());
            isReposted = repostRepository.existsByUserIdAndTrackId(userId, track.getId());
            tier = userService.getUserIfExistsById(userId).getTier();
        } catch (Exception e) {
            trackMapper.toTrackResponseSingle(
                    track,
                    tier,
                    false,
                    false);
        }

        return trackMapper.toTrackResponseSingle(
                track,
                tier,
                isLiked,
                isReposted);
    }
}
