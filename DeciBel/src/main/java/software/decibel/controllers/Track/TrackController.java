package software.decibel.controllers.Track;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.track.*;
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

  // For patching a track
  // Endpoint accepts multipart form data (files)
  @PatchMapping(value = "/{trackId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<TrackPatchResponse> updateTrack(
      @PathVariable Long trackId, @Valid @ModelAttribute TrackPatchRequest request) {

    return ResponseEntity.status((HttpStatus.OK)).body(trackService.updateTrack(trackId, request));
  }

  // For deleting track
  @DeleteMapping("/{trackId}")
  public ResponseEntity<Void> deleteTrack(@PathVariable Long trackId) {
    trackService.deleteTrack(trackId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  // For deleting track's cover
  @DeleteMapping("/{trackId}/cover")
  public ResponseEntity<Void> deleteTrackCover(@PathVariable Long trackId) {
    trackService.deleteTrackCover(trackId);
    return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
  }

  // For returning track's status
  @GetMapping("/{trackId}/status")
  public ResponseEntity<TrackStatusResponse> getTrackStatus(@PathVariable Long trackId) {
    return ResponseEntity.status(HttpStatus.CREATED).body(trackService.getTrackStatus(trackId));
  }

  @GetMapping("/{trackId}/waveform-url")
  public ResponseEntity<TrackWaveFormUrlResponse> getTrackWaveformUrl(@PathVariable Long trackId) {
    return ResponseEntity.status(HttpStatus.OK).body((trackService.getTrackWaveformUrl(trackId)));
  }
}
