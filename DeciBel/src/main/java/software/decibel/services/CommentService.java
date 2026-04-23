package software.decibel.services;

import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import software.decibel.dtos.comment.CommentPageResponse;
import software.decibel.dtos.comment.CommentResponse;
import software.decibel.dtos.comment.CreateCommentRequest;
import software.decibel.dtos.comment.replies.ReplyPageResponse;
import software.decibel.dtos.comment.replies.ReplyResponse;
import software.decibel.entities.Comment;
import software.decibel.entities.Track;
import software.decibel.entities.User;
import software.decibel.enums.NotificationType;
import software.decibel.enums.ResourceType;
import software.decibel.exceptions.custom.InvalidTimestampException;
import software.decibel.exceptions.custom.ReplyToReplyNotAllowedException;
import software.decibel.exceptions.custom.ResourceNotFoundException;
import software.decibel.mappers.CommentMapper;
import software.decibel.repositories.CommentRepository;
import software.decibel.repositories.TrackRepository;
import software.decibel.services.notification.InAppNotificationService;
import software.decibel.services.track.TrackService;
import software.decibel.services.user.UserService;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final InAppNotificationService inAppNotificationService;
    private final CommentRepository commentRepository;
    private final TrackRepository trackRepository;
    private final UserService userService;
    private final TrackService trackService;
    private final CommentMapper commentMapper;
    private final software.decibel.repositories.BlockRepository blockRepository;

    // Add comment to a track
    @Transactional
    public CommentResponse addComment(Long trackId, CreateCommentRequest request) {
        Long userId = JwtService.getCurrentUserId();

        User user = userService.getUserIfExistsById(userId);

        Track track = trackService.getTrackIfExistsById(trackId);

        // Block check: user cannot comment if there is a block relationship with track owner
        if (isUserBlocked(userId, track.getUploader().getId())) {
            throw new ResourceNotFoundException("Track with id " + trackId + " not found");
        }

        // check that timestamp (if given) is not greater than track duration
        if (request.timestampSeconds() != null
                && request.timestampSeconds() > track.getDurationSeconds()) {
            throw new InvalidTimestampException(request.timestampSeconds(), track.getDurationSeconds());
        }

        // update comment count
        track.setCommentCount(track.getCommentCount() + 1);
        trackRepository.save(track);

        Comment comment = commentMapper.toEntity(request, user, track);
        // Notify the track owner that someone commented on their track
        if (track.getUploader() != null) {
            inAppNotificationService.createNotification(
                    track.getUploader().getId(), // Recipient (Track Owner)
                    userId, // Actor (User commenting)
                    NotificationType.COMMENT, // Adjust to match your enum
                    ResourceType.TRACK, // Adjust to match your enum
                    track.getId() // Resource ID

            );
        }
        return commentMapper.toCommentResponse(commentRepository.save(comment));
    }

    @Transactional
    public CommentPageResponse getTrackComments(Long trackId, int page, int size) {
        trackService.getTrackIfExistsById(trackId);

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Comment> result = commentRepository.findByTrackId(trackId, pageable);

        return commentMapper.toPageResponse(result);
    }

    // add a comment reply
    @Transactional
    public ReplyResponse addReply(Long commentId, CreateCommentRequest request) {
        Long userId = JwtService.getCurrentUserId();

        User user = userService.getUserIfExistsById(userId);

        Comment parentComment = getCommentIfExistsById(commentId);

        // Block check: user cannot reply if there is a block relationship with parent comment owner
        if (isUserBlocked(userId, parentComment.getUser().getId())) {
            throw new ResourceNotFoundException("Comment with id " + commentId + " not found");
        }

        // to disable replying to a reply (according to docs one level replies are only allowed)
        if (parentComment.getParentComment() != null) {
            throw new ReplyToReplyNotAllowedException();
        }

        Comment reply
                = commentMapper.toReplyEntity(request, user, parentComment.getTrack(), parentComment);

        // update comment count (replies are considered comments on a track)
        Track track = reply.getTrack();
        track.setCommentCount(track.getCommentCount() + 1);
        trackRepository.save(track);
        if (parentComment.getUser() != null) {
            inAppNotificationService.createNotification(
                    parentComment.getUser().getId(), // Recipient (Author of the parent comment)
                    userId, // Actor (User replying)
                    NotificationType.REPLY, // Adjust to match your enum
                    ResourceType.TRACK, // Adjust to match your enum
                    parentComment.getId() // Resource ID (Could also be savedReply.getId())
            );
        }

        return commentMapper.toReplyResponse(commentRepository.save(reply));
    }

    // get all comment replies (as a page) in asc order of creation date
    @Transactional
    public ReplyPageResponse getReplies(Long commentId, int page, int size) {
        Comment comment = getCommentIfExistsById(commentId);

        // to disable replying to a reply (according to docs one level replies are only allowed)
        if (comment.getParentComment() != null) {
            throw new ReplyToReplyNotAllowedException();
        }

        PageRequest pageable = PageRequest.of(page, size, Sort.by("createdAt").ascending());
        Page<Comment> result = commentRepository.findByParentCommentId(commentId, pageable);

        return commentMapper.toReplyPageResponse(result);
    }

    // Returns comment entity by id and throws exception if not found
    public Comment getCommentIfExistsById(Long commentId) {
        Comment comment = commentRepository
                .findById(commentId)
                .orElseThrow(
                        () -> new ResourceNotFoundException("Comment with id " + commentId + " not found"));

        Long currentUserId = null;
        try {
            currentUserId = JwtService.getCurrentUserId();
        } catch (Exception ignored) {
        }

        if (isUserBlocked(currentUserId, comment.getUser().getId())) {
            throw new ResourceNotFoundException("Comment with id " + commentId + " not found");
        }

        return comment;
    }

    private boolean isUserBlocked(Long currentUserId, Long targetUserId) {
        if (currentUserId == null || targetUserId == null) {
            return false;
        }
        return blockRepository.existsByBlocker_IdAndBlocked_Id(currentUserId, targetUserId)
                || blockRepository.existsByBlocker_IdAndBlocked_Id(targetUserId, currentUserId);
    }
}
