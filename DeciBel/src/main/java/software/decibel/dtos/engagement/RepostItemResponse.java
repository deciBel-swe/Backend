package software.decibel.dtos.engagement;

import java.time.LocalDateTime;

// Represents either a track or playlist repost in the mixed feed
public record RepostItemResponse(
        String type, // "TRACK" or "PLAYLIST"
        Long id,
        String title,
        String coverUrl,
        LocalDateTime repostedAt
        ) {

}
