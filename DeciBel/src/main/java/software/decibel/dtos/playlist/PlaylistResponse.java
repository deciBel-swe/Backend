package software.decibel.dtos.playlist;

import java.time.LocalDateTime;
import java.util.List;

import software.decibel.dtos.track.TrackPageResponse;
import software.decibel.enums.PlaylistType;

public record PlaylistResponse(
        Long id,
        String title,
        PlaylistType type,
        boolean isLiked,
        String description,
        boolean isPrivate,
        String coverArtUrl,
        int totalDurationSeconds,
        int trackCount,
        Long userId,
        String username,
        String displayName,
        List<String> genres,
        LocalDateTime createdAt,
        TrackPageResponse tracks) {

}
