package software.decibel.controllers.User;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.services.user.UserLikeService;

@RestController
@RequestMapping("/users/me")
@RequiredArgsConstructor
public class UserLikeController {

  private final UserLikeService userLikeService;

  // For getting all tracks liked by the user
  @GetMapping("/liked-tracks")
  public ResponseEntity<TrackPageResponse> getLikedTracks(
      @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {

    return ResponseEntity.status(HttpStatus.OK).body(userLikeService.getLikedTracks(page, size));
  }
}
