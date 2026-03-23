package software.decibel.controllers;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.services.TrackService;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserTrackController {

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
