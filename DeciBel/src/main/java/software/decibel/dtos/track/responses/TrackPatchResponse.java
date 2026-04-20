package software.decibel.dtos.track.responses;

import java.time.LocalDate;
import java.util.List;
import software.decibel.enums.TrackAccess;

public record TrackPatchResponse(
    Long id,
    String coverUrl,
    String title,
    String genre,
    String description,
    Boolean isPrivate,
    List<String> tags,
    LocalDate releaseDate,
    TrackAccess access) {}
