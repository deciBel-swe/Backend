package software.decibel.dtos.track.responses;

import java.util.List;

// Different that spring boot's page response so we will make a custom one
public record TrackPageResponse(
    List<TrackResponse> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast) {}
