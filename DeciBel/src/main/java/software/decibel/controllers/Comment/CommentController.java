package software.decibel.controllers.Comment;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.decibel.dtos.comment.CommentPageResponse;
import software.decibel.dtos.comment.CommentResponse;
import software.decibel.dtos.comment.CreateCommentRequest;
import software.decibel.services.CommentService;

@RestController
@RequiredArgsConstructor
public class CommentController {

  private final CommentService commentService;

  // add a comment to a track
  @PostMapping("/tracks/{trackId}/comments")
  public ResponseEntity<CommentResponse> addComment(
      @PathVariable Long trackId, @Valid @RequestBody CreateCommentRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(commentService.addComment(trackId, request));
  }

  // get all comments from a track sorted by newest
  @GetMapping("/tracks/{trackId}/comments")
  public ResponseEntity<CommentPageResponse> getComments(
      @PathVariable Long trackId,
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(commentService.getTrackComments(trackId, page, size));
  }
}
