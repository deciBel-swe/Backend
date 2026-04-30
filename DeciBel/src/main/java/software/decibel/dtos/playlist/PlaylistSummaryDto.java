package software.decibel.dtos.playlist;

import software.decibel.dtos.track.TrackSummaryDTO;
import software.decibel.dtos.user.UserSummaryDTO;

public record PlaylistSummaryDto(
        Long id,
        String title,
        String playlistSlug,
        boolean isLiked,
        boolean isPrivate,
        String coverArtUrl,
        int trackCount,
        UserSummaryDTO owner,
        String genre, // primary genre (single string per Image 3)
        TrackSummaryDTO tracks, // representative/first track
        String secretToken
        ) {

}
