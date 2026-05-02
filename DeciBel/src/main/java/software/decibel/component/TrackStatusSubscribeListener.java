package software.decibel.component;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import software.decibel.dtos.track.responses.TrackStatusResponse;

@Component
@RequiredArgsConstructor
@Slf4j
public class TrackStatusSubscribeListener {

    private final UploadStatusCache uploadStatusCache;
    private final SimpMessagingTemplate messagingTemplate;

    @EventListener
    public void handleSubscribe(SessionSubscribeEvent event) {
        String destination = (String) event.getMessage().getHeaders().get("simpDestination");

        if (destination == null || !destination.startsWith("/topic/track-status/")) {
            return;
        }

        String uploadId = destination.substring("/topic/track-status/".length());
        TrackStatusResponse cached = uploadStatusCache.getStatus(uploadId);
        if (cached == null) {
            return;
        }

        log.info("Replaying cached status {}% for uploadId: {}", cached.progressPercentage(), uploadId);
        messagingTemplate.convertAndSend(destination, cached);
    }

}
