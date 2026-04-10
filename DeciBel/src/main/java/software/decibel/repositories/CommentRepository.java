package software.decibel.repositories;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Modifying;

import software.decibel.entities.Comment;

public interface CommentRepository extends JpaRepository<Comment, Long> {

    Page<Comment> findByTrackId(Long trackId, Pageable pageable);

    Page<Comment> findByParentCommentId(Long parentCommentId, Pageable pageable);

    @Query("DELETE FROM Comment r WHERE r.track.id = :trackId")
    @Modifying
    void deleteAllByTrackId(@Param("trackId") Long trackId);
}
