package software.decibel.controllers.User;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.track.responses.TrackPageResponse;
import software.decibel.services.user.UserLikeService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserLikeController {

    private final UserLikeService userLikeService;

    // For getting all tracks liked by the user
    @GetMapping("/me/liked-tracks")
    public ResponseEntity<TrackPageResponse> getLikedTracks(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.status(HttpStatus.OK).body(userLikeService.getLikedTracks(page, size));
    }

    @GetMapping("/{username}/liked-tracks")
    public ResponseEntity<TrackPageResponse> getLikedTracksByUsername(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.status(HttpStatus.OK).body(userLikeService.getLikedTracksByUsername(username, page, size));
    }

    @GetMapping("/{username}/reposted-tracks")
    public ResponseEntity<TrackPageResponse> getRepostedTracksByUsername(
            @PathVariable String username,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.status(HttpStatus.OK).body(userLikeService.getRepostedTracksByUsername(username, page, size));
    }
}
