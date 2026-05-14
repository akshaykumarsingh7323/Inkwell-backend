package com.inkwell.comment.controller;

import com.inkwell.comment.dto.CommentRequest;
import com.inkwell.comment.dto.CommentResponse;
import com.inkwell.comment.dto.ModerationRequest;
import com.inkwell.comment.dto.UpdateCommentRequest;
import com.inkwell.comment.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
@Tag(name = "Comment Management", description = "Endpoints for creating and moderating comments")
public class CommentController {

    private final CommentService commentService;

    @Operation(summary = "Create a comment", description = "Submits a new comment for a post. Comments are pending by default.")
    @ApiResponse(responseCode = "201", description = "Comment successfully submitted")
    @PostMapping
    public ResponseEntity<CommentResponse> create(
            @Valid @RequestBody CommentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        Long userId = validateUserId(userIdHeader);
        return new ResponseEntity<>(commentService.createComment(request, userId), HttpStatus.CREATED);
    }

    @Operation(summary = "Get comments by post", description = "Retrieves all approved comments for a specific post.")
    @ApiResponse(responseCode = "200", description = "List of approved comments retrieved")
    @GetMapping("/post/{postId}")
    public ResponseEntity<List<CommentResponse>> getByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getApprovedCommentsByPost(postId));
    }

    @GetMapping("/post/{postId}/count")
    public ResponseEntity<Long> getCountByPost(@PathVariable Long postId) {
        return ResponseEntity.ok(commentService.getCommentCountByPost(postId));
    }

    @Operation(summary = "Get pending comments", description = "Retrieves all comments currently awaiting moderation.")
    @ApiResponse(responseCode = "200", description = "List of pending comments retrieved")
    @GetMapping("/pending")
    public ResponseEntity<List<CommentResponse>> getPending(@RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(commentService.getPendingComments());
    }

    @Operation(summary = "Get moderator queue", description = "Retrieves pending comments for the current moderator context.")
    @ApiResponse(responseCode = "200", description = "List of pending comments retrieved")
    @GetMapping("/pending/mine")
    public ResponseEntity<List<CommentResponse>> getPendingForRequester(
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        return ResponseEntity.ok(commentService.getPendingCommentsForRequester(validateUserId(userIdHeader), roleHeader));
    }

    @Operation(summary = "Approve a comment", description = "Moderator action to approve a pending comment.")
    @ApiResponse(responseCode = "200", description = "Comment approved successfully")
    @PutMapping("/{id}/approve")
    public ResponseEntity<CommentResponse> approve(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(commentService.approveComment(id));
    }

    @Operation(summary = "Reject a comment", description = "Moderator action to reject and hide a comment.")
    @ApiResponse(responseCode = "200", description = "Comment rejected successfully")
    @PutMapping("/{id}/reject")
    public ResponseEntity<CommentResponse> reject(
            @PathVariable Long id,
            @RequestHeader("X-User-Role") String roleHeader) {
        ensureAdmin(roleHeader);
        return ResponseEntity.ok(commentService.rejectComment(id));
    }

    @Operation(summary = "Delete a comment", description = "Permanently deletes a comment.")
    @ApiResponse(responseCode = "204", description = "Comment deleted successfully")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        commentService.deleteComment(id, validateUserId(userIdHeader), roleHeader);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}")
    public ResponseEntity<CommentResponse> update(
            @PathVariable Long id,
            @Valid @RequestBody UpdateCommentRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        return ResponseEntity.ok(commentService.updateComment(id, request, validateUserId(userIdHeader), roleHeader));
    }



    @PostMapping("/{id}/like")
    public ResponseEntity<CommentResponse> like(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        return ResponseEntity.ok(commentService.likeComment(id, validateUserId(userIdHeader)));
    }

    @PostMapping("/{id}/unlike")
    public ResponseEntity<CommentResponse> unlike(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader) {
        return ResponseEntity.ok(commentService.unlikeComment(id, validateUserId(userIdHeader)));
    }
    @Operation(summary = "Moderate a comment",
               description = "ADMIN can moderate any comment. AUTHOR can only moderate comments on their own posts.")
    @ApiResponse(responseCode = "200", description = "Comment moderated successfully")
    @ApiResponse(responseCode = "403", description = "Not authorized to moderate this comment")
    @PatchMapping("/{id}/moderate")
    public ResponseEntity<CommentResponse> moderate(
            @PathVariable Long id,
            @Valid @RequestBody ModerationRequest request,
            @RequestHeader(value = "X-User-Id", required = false) String userIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String roleHeader) {
        return ResponseEntity.ok(
            commentService.moderateComment(id, request, validateUserId(userIdHeader), roleHeader));
    }

    private Long validateUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.trim().isEmpty()) {
            throw new com.inkwell.comment.exception.CustomException("User session is missing. Please log in again.", HttpStatus.UNAUTHORIZED);
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            throw new com.inkwell.comment.exception.CustomException("Invalid user identity in session.", HttpStatus.BAD_REQUEST);
        }
    }

    private void ensureAdmin(String roleHeader) {
        if (!"ADMIN".equalsIgnoreCase(roleHeader)) {
            throw new com.inkwell.comment.exception.CustomException("Admin access is required", HttpStatus.FORBIDDEN);
        }
    }

}
