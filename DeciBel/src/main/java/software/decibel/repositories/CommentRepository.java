package software.decibel.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import software.decibel.entities.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {
  Page<Comment> findByTrackId(Long trackId, Pageable pageable);

  Page<Comment> findByParentCommentId(Long parentCommentId, Pageable pageable);
}
