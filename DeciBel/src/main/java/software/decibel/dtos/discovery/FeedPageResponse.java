package software.decibel.dtos.discovery;

import java.util.List;

public record FeedPageResponse(
    List<ResourceRefFullDTO> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast) {}
