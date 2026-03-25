package software.decibel.controllers.Track;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import software.decibel.dtos.track.RepostResponse;
import software.decibel.services.track.TrackService;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackRepostController {

    private final TrackService trackService;

    @PostMapping("/{trackId}/repost")
    public ResponseEntity<RepostResponse> repostTrack(@PathVariable Long trackId) {
        return ResponseEntity.ok(trackService.repostTrack(trackId));
    }

    @DeleteMapping("/{trackId}/repost")
    public ResponseEntity<RepostResponse> removeRepost(@PathVariable Long trackId) {
        return ResponseEntity.ok(trackService.removeRepost(trackId));
    }
}
