package software.decibel.dtos.comment;

import java.time.LocalDateTime;

public record CommentResponse(
    Long id,
    CommentUserResponse user,
    String body,
    Integer timestampSeconds,
    LocalDateTime createdAt) {}
