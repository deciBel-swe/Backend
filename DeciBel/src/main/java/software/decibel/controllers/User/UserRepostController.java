package software.decibel.controllers.User;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.services.user.UserRepostService;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserRepostController {

  private final UserRepostService userRepostService;

  // For getting all tracks liked by the user
  @GetMapping("/repost")
  public ResponseEntity<TrackPageResponse> getRepostedTracks(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

    return ResponseEntity.status(HttpStatus.OK)
        .body(userRepostService.getRepostedTracks(page, size));
  }
}
