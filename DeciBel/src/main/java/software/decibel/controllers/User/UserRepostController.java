package software.decibel.controllers.User;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.services.user.UserRepostService;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserRepostController {

    private final UserRepostService userRepostService;

    // For getting all tracks reposted by the user
    @GetMapping("/repost")
    public ResponseEntity<TrackPageResponse> getRepostedTracks(
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

        return ResponseEntity.status(HttpStatus.OK)
                .body(userRepostService.getRepostedTracks(page, size));
    }
}
