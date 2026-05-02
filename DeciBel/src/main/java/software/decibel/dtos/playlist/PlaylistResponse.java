package software.decibel.dtos.playlist;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Page;

import software.decibel.dtos.track.TrackSummaryDTO;
import software.decibel.dtos.user.UserSummaryDTO;
import software.decibel.enums.PlaylistType;

public record PlaylistResponse(
        Long id,
        String title,
        PlaylistType type,
        boolean isLiked,
        boolean isReposted,
        String description,
        boolean isPrivate,
        String coverArtUrl,
        String playlistSlug,
        int totalDurationSeconds,
        int trackCount,
        UserSummaryDTO owner,
        List<String> genres,
        LocalDateTime createdAt,
        Page<TrackSummaryDTO> trackSummaryDto,
        String firstTrackWaveformUrl,
        String secretToken
        ) {

}
