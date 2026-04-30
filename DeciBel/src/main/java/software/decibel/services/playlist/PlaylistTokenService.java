package software.decibel.services.playlist;

import java.util.UUID;

import org.springframework.stereotype.Service;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.PlaylistTokenResponse;
import software.decibel.entities.Playlist;
import software.decibel.entities.PlaylistToken;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.repositories.PlaylistTokenRepository;

@Service
@RequiredArgsConstructor
public class PlaylistTokenService {

    private final PlaylistTokenRepository playlistTokenRepository;

    // -------------------------------------------------------------------------
    // GET active token (owner-only, enforced by PlaylistService before calling)
    // -------------------------------------------------------------------------
    public PlaylistTokenResponse getActiveToken(Long playlistId) {
        PlaylistToken token = playlistTokenRepository
                .findByPlaylistIdAndIsDeletedFalse(playlistId)
                .orElseThrow(() -> new ResourceNotFoundException(
                "No active secret token exists for playlist " + playlistId
                + ". Use POST /secret-link/regenerate to create one."));

        return new PlaylistTokenResponse(token.getToken());
    }

    // -------------------------------------------------------------------------
    // REGENERATE — soft-delete existing, issue fresh token
    // -------------------------------------------------------------------------
    @Transactional
    public PlaylistTokenResponse regenerateToken(Playlist playlist) {
        return new PlaylistTokenResponse(issueNewToken(playlist));
    }

    // -------------------------------------------------------------------------
    // GET playlist entity by token (used for secret-link access, no auth needed)
    // -------------------------------------------------------------------------
    public Playlist getPlaylistByToken(String token) {
        PlaylistToken playlistToken = playlistTokenRepository
                .findByTokenAndIsDeletedFalse(token)
                .orElseThrow(() -> new ResourceNotFoundException("Invalid or expired playlist token"));

        return playlistToken.getPlaylist();
    }

    /**
     * Soft-deletes any existing active token for the playlist and saves a new
     * one.
     *
     * @return the raw token string
     */
    @Transactional
    public String issueNewToken(Playlist playlist) {
        // Soft-delete any currently active token
        playlistTokenRepository
                .findByPlaylistIdAndIsDeletedFalse(playlist.getId())
                .ifPresent(existing -> {
                    existing.setDeleted(true);
                    playlistTokenRepository.save(existing);
                });

        String raw = UUID.randomUUID().toString();
        playlistTokenRepository.save(
                PlaylistToken.builder()
                        .token(raw)
                        .playlist(playlist)
                        .build());

        return raw;
    }

    @Transactional
    public String resolveSecretToken(Playlist playlist) {
        return playlistTokenRepository
                .findByPlaylistIdAndIsDeletedFalse(playlist.getId())
                .map(software.decibel.entities.PlaylistToken::getToken)
                .orElse(null);
    }

}
