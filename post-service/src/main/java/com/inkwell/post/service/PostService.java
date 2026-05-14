package com.inkwell.post.service;

import com.inkwell.post.dto.PaginatedResponse;
import com.inkwell.post.dto.PostRequest;
import com.inkwell.post.dto.PostResponse;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface PostService {
    PostResponse createPost(PostRequest request, Long authorId);
    PostResponse getPostById(Long postId, Long requesterId);
    PostResponse getPostBySlug(String slug, Long requesterId);
    PaginatedResponse<PostResponse> getPostsByAuthor(Long authorId, Pageable pageable);
    PaginatedResponse<PostResponse> getPublishedPostsByAuthor(Long authorId, Pageable pageable);
    PaginatedResponse<PostResponse> getPublishedPosts(Pageable pageable, Long requesterId);
    PaginatedResponse<PostResponse> getPublishedPostsByCategory(Long categoryId, Pageable pageable, Long requesterId);
    PaginatedResponse<PostResponse> getPublishedPostsByTag(Long tagId, Pageable pageable, Long requesterId);
    PaginatedResponse<PostResponse> searchPosts(String keyword, Pageable pageable, Long requesterId);
    PostResponse updatePost(Long postId, PostRequest request, Long requesterId, String requesterRole);
    PostResponse publishPost(Long postId, Long requesterId, String requesterRole);
    PostResponse unpublishPost(Long postId, Long requesterId, String requesterRole);
    void deletePost(Long postId, Long requesterId, String requesterRole);
    
    // Improved view and like methods
    void incrementViews(Long postId, String sessionId);
    void likePost(Long postId, Long userId);
    void unlikePost(Long postId, Long userId);
    
    Long getPostCount(Long authorId, Long requesterId, String requesterRole);
    List<PostResponse> getTrendingPosts(Long requesterId);
    PaginatedResponse<PostResponse> explorePosts(String sort, Long categoryId, Long tagId, String keyword, Pageable pageable, Long requesterId);
}
