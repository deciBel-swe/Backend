package software.decibel.controllers;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.track.*;
import software.decibel.services.TrackService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

  private final TrackService trackService;

  // Fot getting all of current user's tracks (pageable)
  @GetMapping("/me/tracks")
  public ResponseEntity<TrackPageResponse> getCurrentUserTracks(
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {

    return ResponseEntity.status(HttpStatus.OK).body(trackService.getCurrentUserTracks(page, size));
  }

  // Fot getting all of another user's tracks (pageable)
  @GetMapping("/{userId}/tracks")
  public ResponseEntity<TrackPageResponse> getUserTracks(
      @PathVariable Long userId,
      @RequestParam(defaultValue = "0") @Min(0) int page,
      @RequestParam(defaultValue = "20") @Min(1) int size) {

    return ResponseEntity.status(HttpStatus.OK)
        .body(trackService.getUserTracks(userId, page, size));
  }
}
