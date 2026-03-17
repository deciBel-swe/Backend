package software.decibel.dtos.track;

import java.time.LocalDate;
import java.util.List;

public record TrackPatchResponse(
    Long id,
    String coverUrl,
    String title,
    String genre,
    String description,
    Boolean isPrivate,
    List<String> tags,
    LocalDate releaseDate) {}
