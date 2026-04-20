package software.decibel.controllers.Track;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.InitialTrackResponse;
import software.decibel.dtos.track.requests.TrackPatchRequest;
import software.decibel.dtos.track.requests.TrackUploadRequest;
import software.decibel.dtos.track.responses.TrackPatchResponse;
import software.decibel.dtos.track.responses.TrackPublishResponse;
import software.decibel.dtos.track.responses.TrackResponse;
import software.decibel.dtos.track.responses.TrackStatusResponse;
import software.decibel.dtos.track.responses.TrackUploadResponse;
import software.decibel.dtos.track.responses.TrackWaveFormUrlResponse;
import software.decibel.services.track.TrackService;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackController {

    private final TrackService trackService;

    // For uploading a track
    // Endpoint accepts multipart form data (files)
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<TrackResponse> uploadTrack(
            @Valid @ModelAttribute TrackUploadRequest request) {

        TrackResponse response = trackService.uploadTrack(request, request.uploadId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping(value = "/upload/v2", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<InitialTrackResponse> uploadTrackAsync(
            @Valid @ModelAttribute TrackUploadRequest request) {

        TrackUploadResponse response = trackService.uploadTrackAsync(request, request.uploadId());
        InitialTrackResponse minimalResponse = new InitialTrackResponse(
                response.id(),
                request.title(),
                request.uploadId()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(minimalResponse);
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

    @PostMapping("/{trackId}/publish")
    public ResponseEntity<TrackPublishResponse> publishTrack(@PathVariable Long trackId) {
        return ResponseEntity.status(HttpStatus.OK).body(trackService.publishTrack(trackId));
    }

    // GET /tracks/{trackId} to get track data
    @GetMapping("/{trackId}")
    public ResponseEntity<TrackResponse> getTrack(@PathVariable Long trackId) {
        return ResponseEntity.ok(trackService.getTrackData(trackId));
    }
}
