package software.decibel.controllers.User;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.engagement.RepostItemResponse;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.services.engagement.RepostService;
import software.decibel.services.user.UserRepostService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserRepostController {

    private final UserRepostService userRepostService;
    private final RepostService repostService;

    // For getting all tracks reposted by the user
    @GetMapping("/me/repost")
    public ResponseEntity<TrackPageResponse> getRepostedTracks(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(userRepostService.getRepostedTracks(page, size));
    }

    // GET /users/repost/{username} — mixed track+playlist reposts chronological
    @GetMapping("/repost/{username}")
    public ResponseEntity<Page<RepostItemResponse>> getUserReposts(
            @PathVariable String username,
            Pageable pageable) {
        return ResponseEntity.ok(repostService.getUserReposts(username, pageable));
    }
}
