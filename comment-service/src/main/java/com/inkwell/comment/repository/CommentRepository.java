package com.inkwell.comment.repository;

import com.inkwell.comment.entity.Comment;
import com.inkwell.comment.entity.CommentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface CommentRepository extends JpaRepository<Comment, Long> {
    List<Comment> findByPostIdOrderByCreatedAtAsc(Long postId);
    List<Comment> findByPostIdAndStatusOrderByCreatedAtAsc(Long postId, CommentStatus status);
    List<Comment> findByAuthorIdOrderByCreatedAtDesc(Long authorId);
    List<Comment> findByParentIdOrderByCreatedAtAsc(Long parentId);
    List<Comment> findByStatusOrderByCreatedAtAsc(CommentStatus status);
    List<Comment> findByStatusInOrderByCreatedAtDesc(List<CommentStatus> statuses);
    long countByPostIdAndStatus(Long postId, CommentStatus status);
    void deleteByCommentId(Long commentId);

    /**
     * Bulk soft-delete all non-deleted replies of a parent comment in one query.
     * Avoids N+1 by operating at the DB level.
     */
    @Modifying
    @Query("UPDATE Comment c SET c.status = com.inkwell.comment.entity.CommentStatus.DELETED, " +
           "c.deletedAt = :deletedAt " +
           "WHERE c.parentId = :parentId AND c.status <> com.inkwell.comment.entity.CommentStatus.DELETED")
    int bulkSoftDeleteChildrenByParentId(@Param("parentId") Long parentId,
                                         @Param("deletedAt") LocalDateTime deletedAt);
}

