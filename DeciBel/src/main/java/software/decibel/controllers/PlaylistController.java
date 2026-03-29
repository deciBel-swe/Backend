package software.decibel.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.CreatePlaylistRequest;
import software.decibel.dtos.playlist.PatchPlaylistRequest;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.services.JwtService;
import software.decibel.services.playlist.PlaylistService;

@RestController
@RequestMapping("/playlists")
@RequiredArgsConstructor
public class PlaylistController {

    private final PlaylistService playlistService;

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
    public ResponseEntity<PlaylistResponse> getPlaylist(@PathVariable Long playlistId) {
        return ResponseEntity.ok(playlistService.getPlaylist(playlistId));
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

}
