package software.decibel.dtos.discovery;

import java.util.List;
import software.decibel.dtos.track.TrackSummaryDTO;

public record StationPageResponse(
    List<TrackSummaryDTO> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast) {}
