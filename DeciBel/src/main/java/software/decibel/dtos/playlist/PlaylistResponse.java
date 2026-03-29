package software.decibel.dtos.playlist;

import java.time.LocalDateTime;
import java.util.List;

import software.decibel.enums.PlaylistType;

public record PlaylistResponse(
        Long id,
        String title,
        String slug,
        String description,
        PlaylistType type,
        boolean isPrivate,
        String coverArtUrl,
        int trackCount,
        int totalDurationSeconds,
        List<String> genres,
        Long userId,
        List<Long> trackIds,
        LocalDateTime createdAt,
        boolean isLiked) {

}
