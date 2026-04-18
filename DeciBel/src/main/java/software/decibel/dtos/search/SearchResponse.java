package software.decibel.dtos.search;

import java.util.List;
import software.decibel.dtos.discovery.ResourceRefFullDTO;

public record SearchResponse(
    List<ResourceRefFullDTO> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast) {}
