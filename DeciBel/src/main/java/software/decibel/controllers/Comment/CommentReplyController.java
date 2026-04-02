package software.decibel.controllers.Comment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.comment.CreateCommentRequest;
import software.decibel.dtos.comment.replies.ReplyPageResponse;
import software.decibel.dtos.comment.replies.ReplyResponse;
import software.decibel.services.CommentService;

@RestController
@RequiredArgsConstructor
@RequestMapping("/comments/{commentId}/replies")
public class CommentReplyController {

  private final CommentService commentService;

  // add a reply to a comment
  @PostMapping
  public ResponseEntity<ReplyResponse> addReplyToComment(
      @PathVariable Long commentId, @Valid @RequestBody CreateCommentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(commentService.addReply(commentId, request));
  }

  // get all comment replies (in asc order creation time)
  @GetMapping
  public ResponseEntity<ReplyPageResponse> getCommentReplies(
      @PathVariable Long commentId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.status(HttpStatus.OK)
        .body(commentService.getReplies(commentId, page, size));
  }
}
