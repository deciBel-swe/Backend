package software.decibel.dtos.track;

import java.time.LocalDate;
import java.util.List;

public record TrackResponse(
        Long id,
        String title,
        TrackArtist artist,
        String trackUrl,
        String coverUrl,
        String waveformUrl,
        String genre,
        boolean isReposted,
        boolean isLiked,
        List<String> tags,
        LocalDate releaseDate,
        int playCount,
        int likeCount,
        int repostCount,
        boolean isPrivate,
        int trackDurationSeconds,
        LocalDate uploadDate,
        String description) {

}
