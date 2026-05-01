package software.decibel.controllers.Track;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.simp.annotation.SubscribeMapping;
import org.springframework.stereotype.Controller;
import software.decibel.component.UploadStatusCache;
import software.decibel.dtos.track.responses.TrackStatusResponse;
import software.decibel.enums.TrackState;

@Controller
@RequiredArgsConstructor
@Slf4j
public class TrackStatusSocketController {

    private final UploadStatusCache uploadStatusCache;

    @SubscribeMapping("/topic/track-status/{uploadId}")
    public TrackStatusResponse catchUpLateSubscriber(@DestinationVariable String uploadId) {
        log.info("Frontend subscribed to WS for uploadId: {}", uploadId);

        TrackStatusResponse lastKnownStatus = uploadStatusCache.getStatus(uploadId);

        if (lastKnownStatus != null) {
            log.info("Sending cached status to late subscriber: {}%", lastKnownStatus.progressPercentage());
            return lastKnownStatus;
        }

        log.info("No cache found, sending default UPLOADING state.");
        return new TrackStatusResponse(
                TrackState.UPLOADING, null, 0, "Waiting for server..."
        );
    }

}
