package software.decibel.dtos.track;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record TrackResponse(
    Long id,
    String title,
    TrackArtist artist,
    String trackUrl,
    String coverUrl,
    String waveformUrl,
    String genre,
    List<String> tags,
    LocalDate releaseDate,
    int playCount,
    int likeCount,
    int repostCount,
    LocalDateTime uploadDate,
    String description) {}
