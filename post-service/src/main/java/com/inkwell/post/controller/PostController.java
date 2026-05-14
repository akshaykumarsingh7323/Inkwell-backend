package com.inkwell.post.controller;

import com.inkwell.post.dto.PaginatedResponse;
import com.inkwell.post.dto.PostRequest;
import com.inkwell.post.dto.PostResponse;
import com.inkwell.post.service.PostService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor
@Tag(name = "Post Management", description = "Endpoints for creating, managing, and retrieving blog posts")
public class PostController {

    private final PostService postService;

    @Operation(summary = "Create a new post", description = "Creates a new blog post for the authenticated author.")
    @ApiResponse(responseCode = "201", description = "Post successfully created")
    @PostMapping
    public ResponseEntity<PostResponse> create(
            @Valid @RequestBody PostRequest request,
            @RequestHeader("X-User-Id") String userIdHeader) {
        
        Long authorId = Long.parseLong(userIdHeader);
        return new ResponseEntity<>(postService.createPost(request, authorId), HttpStatus.CREATED);
    }

    @Operation(summary = "Get post by ID", description = "Retrieves the details of a specific post using its unique ID.")
    @ApiResponse(responseCode = "200", description = "Post details retrieved")
    @ApiResponse(responseCode = "404", description = "Post not found")
    @ApiResponse(responseCode = "500", description = "Internal server error")
    @GetMapping("/{id}")
    public ResponseEntity<PostResponse> getById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = (userIdHeader != null) ? Long.parseLong(userIdHeader) : null;
        return ResponseEntity.ok(postService.getPostById(id, requesterId));
    }

    @Operation(summary = "Get post by slug", description = "Retrieves the details of a specific post using its SEO-friendly slug.")
    @ApiResponse(responseCode = "200", description = "Post details retrieved")
    @ApiResponse(responseCode = "404", description = "Post not found")
    @GetMapping("/slug/{slug}")
    public ResponseEntity<PostResponse> getBySlug(
            @PathVariable String slug,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = (userIdHeader != null) ? Long.parseLong(userIdHeader) : null;
        return ResponseEntity.ok(postService.getPostBySlug(slug, requesterId));
    }

    @Operation(summary = "Get posts by author", description = "Retrieves a paginated list of posts written by a specific author.")
    @ApiResponse(responseCode = "200", description = "List of posts retrieved")
    @GetMapping("/author/{authorId}")
    public ResponseEntity<PaginatedResponse<PostResponse>> getByAuthor(
            @PathVariable Long authorId,
            @PageableDefault(size = 10, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(postService.getPostsByAuthor(authorId, pageable));
    }

    @Operation(summary = "Get published posts by author", description = "Retrieves published posts for a public author profile.")
    @ApiResponse(responseCode = "200", description = "List of published author posts retrieved")
    @GetMapping("/public/author/{authorId}")
    public ResponseEntity<PaginatedResponse<PostResponse>> getPublishedByAuthor(
            @PathVariable Long authorId,
            @PageableDefault(size = 10, sort = "publishedAt") Pageable pageable) {
        return ResponseEntity.ok(postService.getPublishedPostsByAuthor(authorId, pageable));
    }

    @Operation(summary = "Get published posts", description = "Retrieves a paginated list of all published posts across the platform.")
    @ApiResponse(responseCode = "200", description = "List of published posts retrieved")
    @GetMapping("/published")
    public ResponseEntity<PaginatedResponse<PostResponse>> getPublished(
            @PageableDefault(size = 10, sort = "publishedAt") Pageable pageable,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = parseUserId(userIdHeader);
        return ResponseEntity.ok(postService.getPublishedPosts(pageable, requesterId));
    }

    @Operation(summary = "Get published posts by category", description = "Retrieves published posts for a category page.")
    @ApiResponse(responseCode = "200", description = "List of published category posts retrieved")
    @GetMapping("/published/category/{categoryId}")
    public ResponseEntity<PaginatedResponse<PostResponse>> getPublishedByCategory(
            @PathVariable Long categoryId,
            @PageableDefault(size = 10, sort = "publishedAt") Pageable pageable,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = parseUserId(userIdHeader);
        return ResponseEntity.ok(postService.getPublishedPostsByCategory(categoryId, pageable, requesterId));
    }

    @Operation(summary = "Get published posts by tag", description = "Retrieves published posts for a tag page.")
    @ApiResponse(responseCode = "200", description = "List of published tag posts retrieved")
    @GetMapping("/published/tag/{tagId}")
    public ResponseEntity<PaginatedResponse<PostResponse>> getPublishedByTag(
            @PathVariable Long tagId,
            @PageableDefault(size = 10, sort = "publishedAt") Pageable pageable,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = parseUserId(userIdHeader);
        return ResponseEntity.ok(postService.getPublishedPostsByTag(tagId, pageable, requesterId));
    }

    @Operation(summary = "Explore posts", description = "Retrieves a paginated list of published posts with advanced sorting and discovery options.")
    @ApiResponse(responseCode = "200", description = "Discovery results retrieved")
    @GetMapping("/explore")
    public ResponseEntity<PaginatedResponse<PostResponse>> explore(
            @RequestParam(defaultValue = "latest") String sort,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) Long tagId,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = parseUserId(userIdHeader);
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size);
        return ResponseEntity.ok(postService.explorePosts(sort, categoryId, tagId, keyword, pageable, requesterId));
    }

    @Operation(summary = "Search posts", description = "Searches for posts based on a keyword in the title or content.")
    @ApiResponse(responseCode = "200", description = "Search results retrieved")
    @GetMapping("/search")
    public ResponseEntity<PaginatedResponse<PostResponse>> search(
            @RequestParam String keyword,
            @PageableDefault(size = 10) Pageable pageable,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = parseUserId(userIdHeader);
        return ResponseEntity.ok(postService.searchPosts(keyword, pageable, requesterId));
    }

    @Operation(summary = "Update post", description = "Updates the content and metadata of an existing post.")
    @ApiResponse(responseCode = "200", description = "Post updated successfully")
    @PutMapping("/{id}")
    public ResponseEntity<PostResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody PostRequest request,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        return ResponseEntity.ok(postService.updatePost(id, request, Long.parseLong(userIdHeader), roleHeader));
    }

    @Operation(summary = "Publish post", description = "Changes the status of a post to PUBLISHED.")
    @ApiResponse(responseCode = "200", description = "Post published successfully")
    @PutMapping("/{id}/publish")
    public ResponseEntity<PostResponse> publish(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        return ResponseEntity.ok(postService.publishPost(id, Long.parseLong(userIdHeader), roleHeader));
    }

    @Operation(summary = "Unpublish post", description = "Changes the status of a post to DRAFT.")
    @ApiResponse(responseCode = "200", description = "Post unpublished successfully")
    @PutMapping("/{id}/unpublish")
    public ResponseEntity<PostResponse> unpublish(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        return ResponseEntity.ok(postService.unpublishPost(id, Long.parseLong(userIdHeader), roleHeader));
    }

    @Operation(summary = "Delete post", description = "Permanently deletes a post from the platform.")
    @ApiResponse(responseCode = "204", description = "Post deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader("X-User-Id") String userIdHeader,
            @RequestHeader("X-User-Role") String roleHeader) {
        postService.deletePost(id, Long.parseLong(userIdHeader), roleHeader);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Increment view count", description = "Increments the view count for a specific post (once per session).")
    @ApiResponse(responseCode = "200", description = "View count incremented or already counted")
    @PostMapping("/{id}/view")
    public ResponseEntity<Void> incrementViews(@PathVariable Long id, HttpServletRequest request) {
        String sessionId = request.getSession().getId();
        postService.incrementViews(id, sessionId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Like post", description = "Adds a like to the specific post (one like per user).")
    @ApiResponse(responseCode = "200", description = "Post liked successfully")
    @PostMapping("/{id}/like")
    public ResponseEntity<Void> like(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long userId = validateUserId(userIdHeader);
        postService.likePost(id, userId);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Unlike post", description = "Removes a like from the specific post.")
    @ApiResponse(responseCode = "200", description = "Post unliked successfully")
    @PostMapping("/{id}/unlike")
    public ResponseEntity<Void> unlike(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long userId = validateUserId(userIdHeader);
        postService.unlikePost(id, userId);
        return ResponseEntity.ok().build();
    }

    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank() || 
            userIdHeader.equalsIgnoreCase("null") || userIdHeader.equalsIgnoreCase("undefined")) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null; // Silent fallback to guest for invalid headers
        }
    }

    private Long validateUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            throw new com.inkwell.post.exception.CustomException(
                "Missing X-User-Id header. User must be authenticated.", 
                HttpStatus.UNAUTHORIZED
            );
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            throw new com.inkwell.post.exception.CustomException(
                "Invalid X-User-Id format: " + userIdHeader, 
                HttpStatus.BAD_REQUEST
            );
        }
    }

    @GetMapping("/count/{authorId}")
    public ResponseEntity<Long> getCount(@PathVariable Long authorId) {
        return ResponseEntity.ok(postService.getPostCount(authorId, null, null));
    }

    @Operation(summary = "Get trending posts", description = "Retrieves top 5 posts based on daily view counts.")
    @GetMapping("/public/trending")
    public ResponseEntity<java.util.List<PostResponse>> getTrending(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long requesterId = parseUserId(userIdHeader);
        return ResponseEntity.ok(postService.getTrendingPosts(requesterId));
    }
}
