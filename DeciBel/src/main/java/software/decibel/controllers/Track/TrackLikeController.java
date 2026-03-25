package software.decibel.controllers.Track;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.track.LikeResponse;
import software.decibel.services.track.TrackService;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackLikeController {

    private final TrackService trackService;

    @PostMapping("/{trackId}/like")
    public ResponseEntity<LikeResponse> likeTrack(@PathVariable Long trackId) {
        return ResponseEntity.ok(trackService.likeTrack(trackId));
    }

    @DeleteMapping("/{trackId}/like")
    public ResponseEntity<LikeResponse> unlikeTrack(@PathVariable Long trackId) {
        return ResponseEntity.ok(trackService.unlikeTrack(trackId));
    }
}
