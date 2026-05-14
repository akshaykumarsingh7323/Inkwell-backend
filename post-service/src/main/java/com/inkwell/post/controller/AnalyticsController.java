package com.inkwell.post.controller;

import com.inkwell.post.dto.PostResponse;
import com.inkwell.post.entity.Post;
import com.inkwell.post.enums.PostStatus;
import com.inkwell.post.exception.CustomException;
import com.inkwell.post.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Admin-only analytics endpoints.
 * All responses are raw data — charting is handled on the frontend.
 */
@RestController
@RequestMapping("/posts/analytics")
@RequiredArgsConstructor
@Tag(name = "Analytics", description = "Admin endpoints for platform analytics")
public class AnalyticsController {

    private final PostRepository postRepository;

    @Operation(summary = "Top 10 most-viewed posts",
               description = "Returns the 10 published posts with the highest view counts. Admin only.")
    @GetMapping("/top-viewed")
    public ResponseEntity<List<Map<String, Object>>> getTopViewedPosts(
            @RequestHeader("X-User-Role") String roleHeader) {

        ensureAdmin(roleHeader);

        List<Post> posts = postRepository.findTop10ByStatusOrderByViewCountDesc(PostStatus.PUBLISHED);
        List<Map<String, Object>> result = posts.stream().map(p -> Map.<String, Object>of(
                "postId",    p.getPostId(),
                "title",     p.getTitle(),
                "slug",      p.getSlug(),
                "authorId",  p.getAuthorId(),
                "viewCount", p.getViewCount() != null ? p.getViewCount() : 0L,
                "likesCount",p.getLikesCount() != null ? p.getLikesCount() : 0L
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    @Operation(summary = "Top 5 authors by total views",
               description = "Returns the top 5 authors ranked by the sum of view counts across all their published posts. Admin only.")
    @GetMapping("/top-authors")
    public ResponseEntity<List<Map<String, Object>>> getTopAuthors(
            @RequestHeader("X-User-Role") String roleHeader) {

        ensureAdmin(roleHeader);

        List<Object[]> rows = postRepository.findTopAuthorsByViewCount(PageRequest.of(0, 5));
        List<Map<String, Object>> result = rows.stream().map(row -> Map.<String, Object>of(
                "authorId",   row[0],
                "totalViews", row[1]
        )).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }

    private void ensureAdmin(String role) {
        if (!"ADMIN".equalsIgnoreCase(role)) {
            throw new CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }
    }
}
