package software.decibel.controllers;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.services.track.TrackService;

@RestController
@RequestMapping("/explore")
@RequiredArgsConstructor
public class ExploreController {

    private final TrackService trackService;

    @GetMapping("/trending")
    public ResponseEntity<TrackPageResponse> getTrendingTracks(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(trackService.getTrendingTracks(page, size));
    }
}
