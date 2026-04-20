package software.decibel.dtos.messaging;

import java.util.List;

public record MessagePageResponse(
    List<MessageResponse> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast
) {}
