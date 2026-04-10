package software.decibel.dtos.comment.replies;

import java.time.LocalDateTime;
import software.decibel.dtos.comment.CommentUserResponse;

public record ReplyResponse(
    Long id,
    CommentUserResponse user,
    String body,
    Long replyToCommentId,
    LocalDateTime createdAt) {}
