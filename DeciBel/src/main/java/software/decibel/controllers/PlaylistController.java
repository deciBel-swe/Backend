package software.decibel.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.Resource;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistSummaryResponse;
import software.decibel.dtos.playlist.PlaylistTokenResponse;
import software.decibel.dtos.playlist.ReorderTracksRequest;
import software.decibel.dtos.playlist.SecretLinkResponse;
import software.decibel.dtos.track.responses.LikeResponse;
import software.decibel.dtos.track.responses.RepostResponse;
import software.decibel.services.JwtService;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.playlist.PlaylistService;

@RestController
@RequestMapping("/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;
    private final LikeService likeService;
    private final RepostService repostService;

    // ── CREATE ────────────────────────────────────────────────────────────────
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistSummaryResponse> createPlaylist(@Valid @ModelAttribute CreatePlaylistRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playlistService.createPlaylist(JwtService.getCurrentUserId(), request));
    }

    @PostMapping(value = "/v2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistResponse> createPlaylistV2(
            @Valid @ModelAttribute CreatePlaylistRequest request,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playlistService.createPlaylistV2(JwtService.getCurrentUserId(), request, pageable));
    }

    // ── GET BY ID ─────────────────────────────────────────────────────────────
    @GetMapping("/{playlistId}")
    public ResponseEntity<PlaylistSummaryResponse> getPlaylist(@PathVariable Long playlistId) {
        return ResponseEntity.ok(playlistService.getPlaylist(playlistId, JwtService.getCurrentUserId()));
    }

    @GetMapping("/{playlistId}/v2")
    public ResponseEntity<PlaylistResponse> getPlaylistV2(
            @PathVariable Long playlistId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(playlistService.getPlaylistV2(playlistId, JwtService.getCurrentUserId(), pageable));
    }

    // ── GET BY TOKEN ──────────────────────────────────────────────────────────
    @GetMapping("/token/{token}")
    public ResponseEntity<PlaylistSummaryResponse> getPlaylistByToken(@PathVariable String token) {
        return ResponseEntity.ok(playlistService.getPlaylistByToken(token, JwtService.getCurrentUserId()));
    }

    @GetMapping("/token/{token}/v2")
    public ResponseEntity<PlaylistResponse> getPlaylistByTokenV2(
            @PathVariable String token,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(playlistService.getPlaylistByTokenV2(token, JwtService.getCurrentUserId(), pageable));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    @PatchMapping(value = "/{playlistId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistSummaryResponse> patchPlaylist(
            @PathVariable Long playlistId,
            @Valid @ModelAttribute PatchPlaylistRequest request) {
        return ResponseEntity.ok(playlistService.patchPlaylist(JwtService.getCurrentUserId(), playlistId, request));
    }

    @PatchMapping(value = "/{playlistId}/v2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistResponse> patchPlaylistV2(
            @PathVariable Long playlistId,
            @Valid @ModelAttribute PatchPlaylistRequest request,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(playlistService.patchPlaylistV2(JwtService.getCurrentUserId(), playlistId, request, pageable));
    }

    // ── TRACK OPERATIONS ──────────────────────────────────────────────────────
    @PostMapping("/{playlistId}/tracks")
    public ResponseEntity<PlaylistSummaryResponse> addTrack(@PathVariable Long playlistId, @RequestParam Long trackId) {
        return ResponseEntity.ok(playlistService.addTrack(JwtService.getCurrentUserId(), playlistId, trackId));
    }

    @PostMapping("/{playlistId}/tracks/v2")
    public ResponseEntity<PlaylistResponse> addTrackV2(
            @PathVariable Long playlistId,
            @RequestParam Long trackId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(playlistService.addTrackV2(JwtService.getCurrentUserId(), playlistId, trackId, pageable));
    }

    @PatchMapping("/{playlistId}/tracks/reorder")
    public ResponseEntity<PlaylistSummaryResponse> reorderTracks(
            @PathVariable Long playlistId,
            @Valid @RequestBody ReorderTracksRequest request) {
        return ResponseEntity.ok(playlistService.reorderTracks(JwtService.getCurrentUserId(), playlistId, request));
    }

    @PatchMapping("/{playlistId}/tracks/reorder/v2")
    public ResponseEntity<PlaylistResponse> reorderTracksV2(
            @PathVariable Long playlistId,
            @Valid @RequestBody ReorderTracksRequest request,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(playlistService.reorderTracksV2(JwtService.getCurrentUserId(), playlistId, request, pageable));
    }

    // ── DELETIONS (No V2 needed, returns Void) ────────────────────────────────
    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long playlistId) {
        playlistService.deletePlaylist(playlistId, JwtService.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{playlistId}/cover")
    public ResponseEntity<Void> deletePlaylistCover(@PathVariable Long playlistId) {
        playlistService.deletePlaylistCover(playlistId, JwtService.getCurrentUserId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<Void> removeTrack(@PathVariable Long playlistId, @PathVariable Long trackId) {
        playlistService.removeTrack(JwtService.getCurrentUserId(), playlistId, trackId);
        return ResponseEntity.noContent().build();
    }

    // ── ENGAGEMENT (Unchanged) ────────────────────────────────────────────────
    @PostMapping("/{playlistId}/like")
    public ResponseEntity<LikeResponse> likePlaylist(@PathVariable Long playlistId) {
        return ResponseEntity.ok(likeService.likePlaylist(JwtService.getCurrentUserId(), playlistId));
    }

    @DeleteMapping("/{playlistId}/like")
    public ResponseEntity<Void> unlikePlaylist(@PathVariable Long playlistId) {
        likeService.unlikePlaylist(JwtService.getCurrentUserId(), playlistId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{playlistId}/repost")
    public ResponseEntity<RepostResponse> repostPlaylist(@PathVariable Long playlistId) {
        return ResponseEntity.ok(repostService.repostPlaylist(JwtService.getCurrentUserId(), playlistId));
    }

    @DeleteMapping("/{playlistId}/reposts")
    public ResponseEntity<Void> unrepostPlaylist(@PathVariable Long playlistId) {
        playlistService.unrepostPlaylist(JwtService.getCurrentUserId(), playlistId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}/liked-playlists")
    public ResponseEntity<Page<PlaylistSummaryResponse>> getLikedPlaylists(@PathVariable String username, Pageable pageable) {
        return ResponseEntity.ok(likeService.getLikedPlaylists(username, pageable));
    }

    @GetMapping("/resolve/{playlistSlug}")
    public ResponseEntity<Resource> resolvePlaylistSlug(@PathVariable String playlistSlug) {
        return ResponseEntity.ok(playlistService.resolvePlaylistSlug(playlistSlug));
    }

    @PostMapping("/{playlistId}/secret-link/regenerate")
    public ResponseEntity<SecretLinkResponse> regenerateSecretLink(@PathVariable Long playlistId) {
        return ResponseEntity.ok(
                playlistService.regenerateSecretLink(playlistId, JwtService.getCurrentUserId())
        );
    }

    @GetMapping("/{playlistId}/secret-link")
    public ResponseEntity<SecretLinkResponse> getSecretLink(@PathVariable Long playlistId) {
        return ResponseEntity.ok(
                playlistService.getSecretLink(playlistId, JwtService.getCurrentUserId())
        );
    }
}
