package software.decibel.controllers;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.track.TrackStatusResponse;
import software.decibel.dtos.track.TrackUploadRequest;
import software.decibel.dtos.track.TrackUploadResponse;
import software.decibel.services.TrackService;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackController {

  private final TrackService trackService;

  // For uploading a track
  // Endpoint accepts multipart form data (files)
  @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<TrackUploadResponse> uploadTrack(
      @Valid @ModelAttribute TrackUploadRequest request) {

    TrackUploadResponse response = trackService.uploadTrack(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  // For deleting track
  @DeleteMapping("/{trackId}")
  public ResponseEntity<Void> deleteTrack(@PathVariable Long trackId) {
    trackService.deleteTrack(trackId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  // For returning track's status
  @GetMapping("/{trackId}/status")
  public ResponseEntity<TrackStatusResponse> getTrackStatus(@PathVariable Long trackId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(trackService.getTrackStatus(trackId));
  }
}
