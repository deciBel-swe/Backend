package software.decibel.controllers.Track;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.LikeResponse;
import software.decibel.services.engagement.LikeService;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackLikeController {

    private final LikeService likeService;

    @PostMapping("/{trackId}/like")
    public ResponseEntity<LikeResponse> likeTrack(@PathVariable Long trackId) {
        return ResponseEntity.ok(likeService.likeTrack(trackId));
    }

    @DeleteMapping("/{trackId}/like")
    public ResponseEntity<LikeResponse> unlikeTrack(@PathVariable Long trackId) {
        return ResponseEntity.ok(likeService.unlikeTrack(trackId));
    }
}
