package software.decibel.controllers.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.PlaylistResponse;
import software.decibel.dtos.playlist.PlaylistSummaryResponse;
import software.decibel.dtos.user.UserProfile;
import software.decibel.services.JwtService;
import software.decibel.services.engagement.LikeService;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.playlist.PlaylistService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserPlaylistController {

    private final PlaylistService playlistService;
    private final LikeService likeService;
    private final RepostService repostService;

    // ── OTHER USERS' PLAYLISTS ────────────────────────────────────────────────
    @GetMapping("/{username}/playlists")
    public ResponseEntity<Page<PlaylistSummaryResponse>> getUserPlaylists(
            @PathVariable String username, Pageable pageable) {
        return ResponseEntity.ok(playlistService.getPublicPlaylistsByUsername(username, pageable));
    }

    @GetMapping("/{username}/playlists/{playlistId}")
    public ResponseEntity<PlaylistSummaryResponse> getUserPlaylist(
            @PathVariable String username, @PathVariable Long playlistId) {
        return ResponseEntity.ok(playlistService.getPublicPlaylistByIdAndUsername(username, playlistId));
    }

    @GetMapping("/{username}/playlists/{playlistId}/v2")
    public ResponseEntity<PlaylistResponse> getUserPlaylistV2(
            @PathVariable String username,
            @PathVariable Long playlistId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(playlistService.getPublicPlaylistByIdAndUsernameV2(username, playlistId, pageable));
    }

    @GetMapping("/{username}/liked-playlists")
    public ResponseEntity<Page<PlaylistSummaryResponse>> getLikedPlaylistsByUsername(
            @PathVariable String username, Pageable pageable) {
        return ResponseEntity.ok(playlistService.getLikedPlaylistsByUsername(username, pageable));
    }

    @GetMapping("/{username}/reposted-playlists")
    public ResponseEntity<Page<PlaylistSummaryResponse>> getRepostedPlaylistsByUsername(
            @PathVariable String username, Pageable pageable) {
        return ResponseEntity.ok(playlistService.getRepostedPlaylistsByUsername(username, pageable));
    }

    // ── CURRENT USER'S PLAYLISTS ──────────────────────────────────────────────
    @GetMapping("/me/playlists")
    public ResponseEntity<Page<PlaylistSummaryResponse>> getCurrentUserPlaylists(Pageable pageable) {
        return ResponseEntity.ok(playlistService.getPlaylistsByUserId(JwtService.getCurrentUserId(), pageable));
    }

    @GetMapping("/me/playlists/{playlistId}")
    public ResponseEntity<PlaylistSummaryResponse> getCurrentUserPlaylist(@PathVariable Long playlistId) {
        return ResponseEntity.ok(playlistService.getOwnedPlaylistById(JwtService.getCurrentUserId(), playlistId));
    }

    @GetMapping("/me/playlists/{playlistId}/v2")
    public ResponseEntity<PlaylistResponse> getCurrentUserPlaylistV2(
            @PathVariable Long playlistId,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(playlistService.getOwnedPlaylistByIdV2(JwtService.getCurrentUserId(), playlistId, pageable));
    }

    // ── ENGAGEMENT LISTS ──────────────────────────────────────────────────────
    @GetMapping("/playlists/{playlistId}/like")
    public ResponseEntity<Page<UserProfile>> getPlaylistLikers(@PathVariable Long playlistId, Pageable pageable) {
        return ResponseEntity.ok(likeService.getPlaylistLikers(playlistId, pageable));
    }

    @GetMapping("/playlists/{playlistId}/reposters")
    public ResponseEntity<Page<UserProfile>> getPlaylistReposters(@PathVariable Long playlistId, Pageable pageable) {
        return ResponseEntity.ok(repostService.getPlaylistReposters(playlistId, pageable));
    }
}
