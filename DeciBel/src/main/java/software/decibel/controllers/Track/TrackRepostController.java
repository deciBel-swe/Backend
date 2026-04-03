package software.decibel.controllers.Track;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import software.decibel.dtos.track.RepostResponse;
import software.decibel.services.engagement.RepostService;

@RestController
@RequestMapping("/tracks")
@RequiredArgsConstructor
public class TrackRepostController {

    private final RepostService repostService;

    @PostMapping("/{trackId}/repost")
    public ResponseEntity<RepostResponse> repostTrack(@PathVariable Long trackId) {
        return ResponseEntity.ok(repostService.repostTrack(trackId));
    }

    @DeleteMapping("/{trackId}/repost")
    public ResponseEntity<RepostResponse> removeRepost(@PathVariable Long trackId) {
        return ResponseEntity.ok(repostService.removeRepost(trackId));
    }
}
