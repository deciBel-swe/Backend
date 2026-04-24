package software.decibel.dtos.search;

import java.util.List;

import software.decibel.dtos.discovery.ResourceItemDto;

public record SearchResponse(
        List<ResourceItemDto> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean isLast) {

}
