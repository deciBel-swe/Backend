package software.decibel.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.Resource;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistTokenResponse;
import software.decibel.dtos.playlist.ReorderTracksRequest;
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

    // POST /playlists
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistResponse> createPlaylist(
            @Valid @ModelAttribute CreatePlaylistRequest request) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playlistService.createPlaylist(currentUserId, request));
    }

    // GET /playlists/{playlistId} 
    @GetMapping("/{playlistId}")
    public ResponseEntity<PlaylistResponse> getPlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.getPlaylist(playlistId, currentUserId));
    }

    // GET /playlists/token/{token} — secret-link access, no auth required
    @GetMapping("/token/{token}")
    public ResponseEntity<PlaylistResponse> getPlaylistByToken(@PathVariable String token) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.getPlaylistByToken(token, currentUserId));
    }

    // ── UPDATE ────────────────────────────────────────────────────────────────
    // PATCH /playlists/{playlistId} — owner only
    @PatchMapping(value = "/{playlistId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistResponse> patchPlaylist(
            @PathVariable Long playlistId,
            @Valid @ModelAttribute PatchPlaylistRequest request) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.patchPlaylist(currentUserId, playlistId, request));
    }

    // ── DELETE ────────────────────────────────────────────────────────────────
    // DELETE /playlists/{playlistId} — owner only
    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        playlistService.deletePlaylist(playlistId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // DELETE /playlists/{playlistId}/cover — remove cover art (owner only)
    @DeleteMapping("/{playlistId}/cover")
    public ResponseEntity<Void> deletePlaylistCover(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        playlistService.deletePlaylistCover(playlistId, currentUserId);
        return ResponseEntity.noContent().build();
    }

    // ── TRACKS ────────────────────────────────────────────────────────────────
    // POST /playlists/{playlistId}/tracks?trackId= — owner only
    @PostMapping("/{playlistId}/tracks")
    public ResponseEntity<PlaylistResponse> addTrack(
            @PathVariable Long playlistId,
            @RequestParam Long trackId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.addTrack(currentUserId, playlistId, trackId));
    }

    // DELETE /playlists/{playlistId}/tracks/{trackId} — owner only
    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<Void> removeTrack(
            @PathVariable Long playlistId,
            @PathVariable Long trackId) {
        Long currentUserId = JwtService.getCurrentUserId();
        playlistService.removeTrack(currentUserId, playlistId, trackId);
        return ResponseEntity.noContent().build();
    }

    // PATCH /playlists/{playlistId}/tracks/reorder — owner only
    @PatchMapping("/{playlistId}/tracks/reorder")
    public ResponseEntity<PlaylistResponse> reorderTracks(
            @PathVariable Long playlistId,
            @Valid @RequestBody ReorderTracksRequest request) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.reorderTracks(playlistId, request, currentUserId));
    }

    // ── SECRET TOKEN ──────────────────────────────────────────────────────────
    // GET /playlists/{playlistId}/secret-link
    @GetMapping("/{playlistId}/secret-link")
    public ResponseEntity<PlaylistTokenResponse> getToken(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.getToken(currentUserId, playlistId));
    }

    // POST /playlists/{playlistId}/secret-link/regenerate — generate/regenerate (owner only)
    @PostMapping("/{playlistId}/secret-link/regenerate")
    public ResponseEntity<PlaylistTokenResponse> regenerateToken(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.generateToken(currentUserId, playlistId));
    }

    // ── ENGAGEMENT ────────────────────────────────────────────────────────────
    // POST /playlists/{playlistId}/like — any authenticated user
    @PostMapping("/{playlistId}/like")
    public ResponseEntity<LikeResponse> likePlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(likeService.likePlaylist(currentUserId, playlistId));
    }

    // DELETE /playlists/{playlistId}/like — any authenticated user
    @DeleteMapping("/{playlistId}/like")
    public ResponseEntity<Void> unlikePlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        likeService.unlikePlaylist(currentUserId, playlistId);
        return ResponseEntity.noContent().build();
    }

    // POST /playlists/{playlistId}/repost — any authenticated user
    @PostMapping("/{playlistId}/repost")
    public ResponseEntity<RepostResponse> repostPlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(repostService.repostPlaylist(currentUserId, playlistId));
    }

    // DELETE /playlists/{playlistId}/reposts — any authenticated user
    @DeleteMapping("/{playlistId}/reposts")
    public ResponseEntity<Void> unrepostPlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        playlistService.unrepostPlaylist(currentUserId, playlistId);
        return ResponseEntity.noContent().build();
    }

    // GET /playlists/{username}/liked-playlists — any user
    @GetMapping("/{username}/liked-playlists")
    public ResponseEntity<Page<PlaylistResponse>> getLikedPlaylists(
            @PathVariable String username,
            Pageable pageable) {
        return ResponseEntity.ok(likeService.getLikedPlaylists(username, pageable));
    }

    // slug resolver
    @GetMapping("/resolve/{playlistSlug}")
    public ResponseEntity<Resource> resolvePlaylistSlug(
            @PathVariable String playlistSlug) {
        return ResponseEntity.ok(playlistService.resolvePlaylistSlug(playlistSlug));
    }
}
