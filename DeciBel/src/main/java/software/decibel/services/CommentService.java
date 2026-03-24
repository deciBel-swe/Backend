package software.decibel.services;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import software.decibel.dtos.comment.CommentPageResponse;
import software.decibel.dtos.comment.CommentResponse;
import software.decibel.dtos.comment.CreateCommentRequest;
import software.decibel.entities.Comment;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.exceptions.custom.InvalidTimestampException;
import software.decibel.mappers.CommentMapper;
import software.decibel.repositories.CommentRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.user.UserService;

@Service
@RequiredArgsConstructor
public class CommentService {

  private final CommentRepository commentRepository;
  private final TrackRepository trackRepository;
  private final UserService userService;
  private final TrackService trackService;
  private final CommentMapper commentMapper;

  // Add comment to a track
  public CommentResponse addComment(Long trackId, CreateCommentRequest request) {
    Long userId = JwtService.getCurrentUserId();

    User user = userService.getUserIfExistsById(userId);

    Track track = trackService.getTrackIfExistsById(trackId);
    // check that timestamp (if given) is not greater than track duration
    if (request.timestampSeconds() != null
        && request.timestampSeconds() > track.getDurationSeconds()) {
      throw new InvalidTimestampException(request.timestampSeconds(), track.getDurationSeconds());
    }

    Comment comment = commentMapper.toEntity(request, user, track);
    return commentMapper.toCommentResponse(commentRepository.save(comment));
  }

  public CommentPageResponse getTrackComments(Long trackId, int page, int size) {
    trackService.getTrackIfExistsById(trackId);

    PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
    Page<Comment> result = commentRepository.findByTrackId(trackId, pageable);

    return commentMapper.toPageResponse(result);
  }
}
