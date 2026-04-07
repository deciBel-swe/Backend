package software.decibel.controllers;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistTokenResponse;
import software.decibel.dtos.playlist.ReorderTracksRequest;
import software.decibel.dtos.track.LikeResponse;
import software.decibel.dtos.track.RepostResponse;
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

    // POST /playlists — create a playlist
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistResponse> createPlaylist(
            @Valid @ModelAttribute CreatePlaylistRequest request) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(playlistService.createPlaylist(currentUserId, request));
    }

    // PATCH /playlists/{playlistId} — update a playlist
    @PatchMapping(value = "/{playlistId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<PlaylistResponse> patchPlaylist(
            @PathVariable Long playlistId,
            @Valid @ModelAttribute PatchPlaylistRequest request) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.patchPlaylist(currentUserId, playlistId, request));
    }

    // GET /playlists/{playlistId} — get a playlist
    @GetMapping("/{playlistId}")
    public ResponseEntity<PlaylistResponse> getPlaylist(
            @PathVariable Long playlistId,
            @PageableDefault(size = 20) Pageable trackPageable) {

        return ResponseEntity.ok(playlistService.getPlaylist(playlistId, trackPageable));
    }

    // POST /playlists/{playlistId}/tracks — add a track to a playlist
    @PostMapping("/{playlistId}/tracks")
    public ResponseEntity<PlaylistResponse> addTrack(
            @PathVariable Long playlistId,
            @RequestParam Long trackId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.addTrack(currentUserId, playlistId, trackId));
    }

    // DELETE /playlists/{playlistId}/tracks/{trackId} — remove a track from a playlist
    @DeleteMapping("/{playlistId}/tracks/{trackId}")
    public ResponseEntity<PlaylistResponse> removeTrack(
            @PathVariable Long playlistId,
            @PathVariable Long trackId) {
        Long currentUserId = JwtService.getCurrentUserId();

        playlistService.removeTrack(currentUserId, playlistId, trackId);
        return ResponseEntity.noContent().build();
    }

    // DELETE /playlists/{playlistId}
    @DeleteMapping("/{playlistId}")
    public ResponseEntity<Void> deletePlaylist(@PathVariable Long playlistId) {
        playlistService.deletePlaylist(playlistId);
        return ResponseEntity.noContent().build();
    }

    // PATCH /playlists/{playlistId}/tracks/reorder — reorder tracks
    @PatchMapping("/{playlistId}/tracks/reorder")
    public ResponseEntity<PlaylistResponse> reorderTracks(
            @PathVariable Long playlistId,
            @Valid @RequestBody ReorderTracksRequest request) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.reorderTracks(currentUserId, playlistId, request));
    }

    // GET /playlists/token/{token} — get playlist by secret token
    @GetMapping("/token/{token}")
    public ResponseEntity<PlaylistResponse> getPlaylistByToken(@PathVariable String token) {
        return ResponseEntity.ok(playlistService.getPlaylistByToken(token));
    }

    // POST /playlists/{playlistId}/secret-link/regenerate — generate secret token
    @PostMapping("/{playlistId}/secret-link/regenerate")
    public ResponseEntity<PlaylistTokenResponse> generateToken(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.generateToken(currentUserId, playlistId));
    }

    // POST /playlists/{playlistId}/like — like a playlist
    @PostMapping("/{playlistId}/like")
    public ResponseEntity<LikeResponse> likePlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(likeService.likePlaylist(currentUserId, playlistId));
    }

    // DELETE /playlists/{playlistId}/like — unlike a playlist
    @DeleteMapping("/{playlistId}/like")
    public ResponseEntity<Void> unlikePlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        likeService.unlikePlaylist(currentUserId, playlistId);
        return ResponseEntity.noContent().build();
    }

    // POST /playlists/{playlistId}/repost — repost a playlist
    @PostMapping("/{playlistId}/repost")
    public ResponseEntity<RepostResponse> repostPlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(repostService.repostPlaylist(currentUserId, playlistId));
    }

    // DELETE /playlists/{playlistId}/reposts
    @DeleteMapping("/{playlistId}/reposts")
    public ResponseEntity<Void> unrepostPlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        playlistService.unrepostPlaylist(currentUserId, playlistId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{username}/liked-playlists")
    public ResponseEntity<Page<PlaylistResponse>> getLikedPlaylists(
            @PathVariable String username,
            Pageable pageable) {
        return ResponseEntity.ok(likeService.getLikedPlaylists(username, pageable));
    }

}
