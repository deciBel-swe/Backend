package software.decibel.mappers;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.springframework.data.domain.Page;
import software.decibel.dtos.comment.CommentPageResponse;
import software.decibel.dtos.comment.CommentResponse;
import software.decibel.dtos.comment.CommentUserResponse;
import software.decibel.dtos.comment.CreateCommentRequest;
import software.decibel.entities.Comment;
import software.decibel.entities.Track;
import software.decibel.entities.User;

@Mapper(componentModel = "spring")
public interface CommentMapper {

  // Comment entity -> CommentResponse DTO
  @Mapping(target = "body", source = "content")
  CommentResponse toCommentResponse(Comment comment);

  // CreateCommentRequest DTO -> Comment entity
  @Mapping(target = "id", ignore = true)
  @Mapping(target = "createdAt", ignore = true)
  @Mapping(target = "content", source = "request.body")
  Comment toEntity(CreateCommentRequest request, User user, Track track);

  // User entity -> CommentUserResponse DTO
  CommentUserResponse toCommentUserResponse(User user);

  // Comment Page -> CommentPageResponse DTO
  // default so i can write my own method
  default CommentPageResponse toPageResponse(Page<Comment> page) {
    return new CommentPageResponse(
        page.getContent().stream().map(this::toCommentResponse).toList(),
        page.getNumber(),
        page.getSize(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.isLast());
  }
}
