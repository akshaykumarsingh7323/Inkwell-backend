package com.inkwell.post.repository;

import com.inkwell.post.entity.Post;
import com.inkwell.post.enums.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import org.springframework.data.repository.query.Param;
import java.util.Optional;
import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    Optional<Post> findBySlug(String slug);
    Optional<Post> findBySlugAndStatus(String slug, PostStatus status);
    Optional<Post> findByPostId(Long postId);

    Page<Post> findByAuthorIdAndStatusNot(Long authorId, PostStatus status, Pageable pageable);
    Page<Post> findByAuthorIdAndStatus(Long authorId, PostStatus status, Pageable pageable);
    
    Page<Post> findByStatus(PostStatus status, Pageable pageable);
    Page<Post> findByCategoryIdAndStatus(Long categoryId, PostStatus status, Pageable pageable);
    Page<Post> findByPostIdInAndStatus(List<Long> postIds, PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE (LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%'))) AND p.status = :status")
    Page<Post> searchPosts(@Param("keyword") String keyword, @Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE (" +
           "LOWER(p.title) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "LOWER(p.content) LIKE LOWER(CONCAT('%', :keyword, '%')) OR " +
           "(p.postId IN :tagPostIds)" +
           ") AND p.status = :status")
    Page<Post> searchPostsWithTags(@Param("keyword") String keyword, @Param("tagPostIds") List<Long> tagPostIds, @Param("status") PostStatus status, Pageable pageable);

    Long countByAuthorId(Long authorId);

    boolean existsBySlug(String slug);

    @Modifying
    @Query("UPDATE Post p SET p.viewCount = p.viewCount + 1 WHERE p.postId = :postId")
    void incrementViewCount(@org.springframework.data.repository.query.Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = p.likesCount + 1 WHERE p.postId = :postId")
    void incrementLikesCount(@org.springframework.data.repository.query.Param("postId") Long postId);

    @Modifying
    @Query("UPDATE Post p SET p.likesCount = CASE WHEN p.likesCount > 0 THEN p.likesCount - 1 ELSE 0 END WHERE p.postId = :postId")
    void decrementLikesCount(@org.springframework.data.repository.query.Param("postId") Long postId);

    // ──────────────────────────────────────────────────────────────────────────
    // Explore / Discovery Queries
    // ──────────────────────────────────────────────────────────────────────────

    @Query("SELECT p FROM Post p WHERE p.status = :status ORDER BY p.viewCount DESC, p.publishedAt DESC")
    Page<Post> findAllPublishedOrderByViewCountDesc(@Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status ORDER BY p.likesCount DESC, p.publishedAt DESC")
    Page<Post> findAllPublishedOrderByLikesCountDesc(@Param("status") PostStatus status, Pageable pageable);

    /**
     * Trending Score = (likesCount * 3) + (viewCount * 1) + (commentsCount * 2)
     */
    @Query("SELECT p FROM Post p WHERE p.status = :status " +
           "ORDER BY ((COALESCE(p.likesCount, 0) * 3) + (COALESCE(p.viewCount, 0) * 1) + (COALESCE(p.commentsCount, 0) * 2)) DESC, p.publishedAt DESC")
    Page<Post> findAllPublishedTrending(@Param("status") PostStatus status, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.status = :status AND p.categoryId = :categoryId " +
           "ORDER BY p.publishedAt DESC")
    Page<Post> findByCategoryIdAndStatusOrderByPublishedAtDesc(@Param("categoryId") Long categoryId, @Param("status") PostStatus status, Pageable pageable);

    // ──────────────────────────────────────────────────────────────────────────
    // Analytics queries
    // ──────────────────────────────────────────────────────────────────────────

    /** Top 10 most-viewed published posts. */
    List<Post> findTop10ByStatusOrderByViewCountDesc(PostStatus status);

    /** Top 5 daily most-viewed published posts. */
    List<Post> findTop5ByStatusOrderByDailyViewCountDesc(PostStatus status);

    /**
     * Top authors by total view count across all their published posts.
     * Returns Object[] rows: [authorId (Long), totalViews (Long)]
     */
    @Query("SELECT p.authorId, SUM(p.viewCount) AS totalViews " +
           "FROM Post p WHERE p.status = 'PUBLISHED' " +
           "GROUP BY p.authorId ORDER BY totalViews DESC")
    List<Object[]> findTopAuthorsByViewCount(Pageable pageable);
}

