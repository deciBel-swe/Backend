package software.decibel.dtos.comment.replies;

import java.util.List;

public record ReplyPageResponse(
    List<ReplyResponse> content,
    int pageNumber,
    int pageSize,
    long totalElements,
    int totalPages,
    boolean isLast) {}
