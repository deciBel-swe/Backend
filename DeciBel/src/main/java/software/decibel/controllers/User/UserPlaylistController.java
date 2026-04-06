package software.decibel.controllers.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.playlist.PlaylistResponse;
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

    // GET /users/{username}/playlists — all public playlists of another user
    @GetMapping("/{username}/playlists")
    public ResponseEntity<Page<PlaylistResponse>> getUserPlaylists(
            @PathVariable String username,
            Pageable pageable) {
        return ResponseEntity.ok(playlistService.getPublicPlaylistsByUsername(username, pageable));
    }

    // GET /users/{username}/playlists/{playlistId} — a specific playlist belonging to another user
    @GetMapping("/{username}/playlists/{playlistId}")
    public ResponseEntity<PlaylistResponse> getUserPlaylist(
            @PathVariable String username,
            @PathVariable Long playlistId) {
        return ResponseEntity.ok(playlistService.getPublicPlaylistByIdAndUsername(username, playlistId));
    }

    // GET /users/{username}/liked-playlists — playlists liked by another user
    @GetMapping("/{username}/liked-playlists")
    public ResponseEntity<Page<PlaylistResponse>> getLikedPlaylistsByUsername(
            @PathVariable String username,
            Pageable pageable) {
        return ResponseEntity.ok(playlistService.getLikedPlaylistsByUsername(username, pageable));
    }

    // GET /users/{username}/reposted-playlists — playlists reposted by another user
    @GetMapping("/{username}/reposted-playlists")
    public ResponseEntity<Page<PlaylistResponse>> getRepostedPlaylistsByUsername(
            @PathVariable String username,
            Pageable pageable) {
        return ResponseEntity.ok(playlistService.getRepostedPlaylistsByUsername(username, pageable));
    }

    // GET /users/me/playlists — all playlists (public + private) of the current user
    @GetMapping("/me/playlists")
    public ResponseEntity<Page<PlaylistResponse>> getCurrentUserPlaylists(Pageable pageable) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.getPlaylistsByUserId(currentUserId, pageable));
    }

    // GET /users/me/playlists/{playlistId} — a specific playlist owned by the current user
    @GetMapping("/me/playlists/{playlistId}")
    public ResponseEntity<PlaylistResponse> getCurrentUserPlaylist(@PathVariable Long playlistId) {
        Long currentUserId = JwtService.getCurrentUserId();
        return ResponseEntity.ok(playlistService.getOwnedPlaylistById(currentUserId, playlistId));
    }
    // GET /playlists/{playlistId}/likes — all users who liked a playlist

    @GetMapping("/playlists/{playlistId}/like")
    public ResponseEntity<Page<UserProfile>> getPlaylistLikers(
            @PathVariable Long playlistId,
            Pageable pageable) {
        return ResponseEntity.ok(likeService.getPlaylistLikers(playlistId, pageable));
    }

    // GET /playlists/{playlistId}/reposters — all users who reposted a playlist
    @GetMapping("/playlists/{playlistId}/reposters")
    public ResponseEntity<Page<UserProfile>> getPlaylistReposters(
            @PathVariable Long playlistId,
            Pageable pageable) {
        return ResponseEntity.ok(repostService.getPlaylistReposters(playlistId, pageable));
    }
}
