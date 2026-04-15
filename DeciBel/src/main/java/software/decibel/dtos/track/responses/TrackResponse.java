package software.decibel.dtos.track.responses;

import java.time.LocalDate;
import java.util.List;
import software.decibel.enums.TrackAccess;

public record TrackResponse(
    Long id,
    String title,
    TrackArtist artist,
    String trackUrl,
    String trackPreviewUrl,
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
    String description,
    TrackAccess access) {}
