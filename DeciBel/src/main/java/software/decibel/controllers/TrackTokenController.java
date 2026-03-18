package software.decibel.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.track.TrackTokenResponse;
import software.decibel.services.TrackTokenService;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackTokenController {

  private final TrackTokenService trackTokenService;

  // Gets the latest (only active) secret token for the track
  @GetMapping("/{trackId}/secret-token")
  public ResponseEntity<TrackTokenResponse> getToken(@PathVariable Long trackId) {
    return ResponseEntity.status(HttpStatus.OK).body(trackTokenService.getActiveToken(trackId));
  }

  // Generate a new secret token and return it (soft deletes the other tokens)
  @PostMapping("/{trackId}/regenerate-token")
  public ResponseEntity<TrackTokenResponse> regenerateToken(@PathVariable Long trackId) {
    return ResponseEntity.status(HttpStatus.OK).body(trackTokenService.regenerateToken(trackId));
  }
}
