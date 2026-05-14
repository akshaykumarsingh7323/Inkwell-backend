package com.inkwell.comment.service.impl;

import com.inkwell.comment.dto.CommentRequest;
import com.inkwell.comment.dto.CommentResponse;
import com.inkwell.comment.dto.ModerationRequest;
import com.inkwell.comment.dto.UpdateCommentRequest;
import com.inkwell.comment.entity.Comment;
import com.inkwell.comment.entity.CommentLike;
import com.inkwell.comment.entity.CommentStatus;
import com.inkwell.comment.exception.CustomException;
import com.inkwell.comment.repository.CommentLikeRepository;
import com.inkwell.comment.repository.CommentRepository;
import com.inkwell.comment.client.PostClient;
import com.inkwell.comment.client.AuthClient;
import com.inkwell.comment.dto.NotificationEvent;
import com.inkwell.comment.event.CommentEventPublisher;
import com.inkwell.comment.service.CommentService;
import com.inkwell.comment.util.HtmlSanitizer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostClient postClient;
    private final AuthClient authClient;
    private final CommentEventPublisher commentEventPublisher;

    @Value("${comment.auto-approve:false}")
    private boolean autoApprove;

    @Override
    @Transactional
    public CommentResponse createComment(CommentRequest request, Long requesterId) {
        CommentStatus initialStatus = autoApprove ? CommentStatus.APPROVED : CommentStatus.PENDING;
        validateCommentRequest(request);

        String sanitizedContent = HtmlSanitizer.sanitize(request.getContent());

        Comment comment = Comment.builder()
                .postId(request.getPostId())
                .authorId(requesterId)
                .parentId(request.getParentId())
                .content(sanitizedContent)
                .status(initialStatus)
                .build();

        log.info("Comment created for postId={} with status={}", request.getPostId(), initialStatus);
        Comment savedComment = commentRepository.save(comment);
        
        if (initialStatus == CommentStatus.APPROVED) {
            triggerCommentNotifications(savedComment, "NEW_COMMENT", "New comment on your post");
        } else if (initialStatus == CommentStatus.PENDING) {
            triggerCommentNotifications(savedComment, "NEW_COMMENT", "New comment awaiting moderation on your post");
        }
        
        return mapToResponse(savedComment);
    }

    private void triggerCommentNotifications(Comment comment, String type, String baseMessage) {
        try {
            // 1. Notify Post Author
            Map<String, Object> post = postClient.getPostById(comment.getPostId());
            if (post == null || !post.containsKey("authorId")) {
                log.warn("Post not found or author missing for postId={}", comment.getPostId());
                return;
            }

            Long postAuthorId = Long.valueOf(post.get("authorId").toString());
            String postTitle = post.getOrDefault("title", "your post").toString();
            String postSlug = post.getOrDefault("slug", "").toString();
            
            if (!postAuthorId.equals(comment.getAuthorId())) {
                String actorName = "Someone";
                try {
                    Map<String, Object> actor = authClient.getUserById(comment.getAuthorId());
                    actorName = actor.getOrDefault("username", "Someone").toString();
                } catch (Exception e) {
                    log.warn("Failed to fetch actor info for userId={}", comment.getAuthorId());
                }

                String structuredMessage = String.format("%s commented on your post \"%s\":\n\n%s", 
                    actorName, postTitle, comment.getContent());
                
                commentEventPublisher.publishNotificationEvent(NotificationEvent.builder()
                        .userId(postAuthorId)
                        .type(type)
                        .message(structuredMessage.length() > 500 ? structuredMessage.substring(0, 497) + "..." : structuredMessage)
                        .metadata(Map.of(
                            "postId", comment.getPostId().toString(),
                            "postSlug", postSlug,
                            "commentId", comment.getCommentId().toString(),
                            "actorId", comment.getAuthorId().toString(),
                            "actorName", actorName
                        ))
                        .build());
            }

            // 2. Notify Parent Comment Author (if reply and approved)
            if (comment.getParentId() != null && comment.getStatus() == CommentStatus.APPROVED) {
                commentRepository.findById(comment.getParentId()).ifPresent(parent -> {
                    if (!parent.getAuthorId().equals(comment.getAuthorId())) {
                        commentEventPublisher.publishNotificationEvent(NotificationEvent.builder()
                                .userId(parent.getAuthorId())
                                .type("COMMENT_REPLY")
                                .message("Someone replied to your comment")
                                .metadata(Map.of("postId", comment.getPostId().toString(), "commentId", comment.getCommentId().toString()))
                                .build());
                    }
                });
            }
        } catch (Exception e) {
            log.error("Failed to trigger comment notifications", e);
        }
    }

    @Override
    public List<CommentResponse> getApprovedCommentsByPost(Long postId) {
        return commentRepository.findByPostIdAndStatusOrderByCreatedAtAsc(postId, CommentStatus.APPROVED)
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public List<CommentResponse> getPendingComments() {
        return commentRepository.findByStatusInOrderByCreatedAtDesc(List.of(CommentStatus.PENDING, CommentStatus.APPROVED))
                .stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Override
    public List<CommentResponse> getPendingCommentsForRequester(Long requesterId, String requesterRole) {
        log.info("Fetching comments for requester: {} with role: {}", requesterId, requesterRole);
        if ("ADMIN".equalsIgnoreCase(requesterRole)) {
            log.info("Requester is ADMIN, returning all pending/approved comments");
            return getPendingComments();
        }

        if (!"AUTHOR".equalsIgnoreCase(requesterRole)) {
            log.warn("Non-author/admin role {} attempted to access moderation queue", requesterRole);
            throw new CustomException("You do not have permission to moderate comments", HttpStatus.FORBIDDEN);
        }

        Map<Long, Long> postAuthorIds = new HashMap<>();
        List<Comment> allComments = commentRepository.findByStatusInOrderByCreatedAtDesc(List.of(CommentStatus.PENDING, CommentStatus.APPROVED));
        log.info("Found {} total pending/approved comments in database to filter", allComments.size());
        
        List<CommentResponse> comments = allComments.stream()
                .filter(comment -> {
                    boolean owned = isOwnedByAuthor(comment.getPostId(), requesterId, postAuthorIds);
                    log.info("Checking comment ID {}: postId={}, requesterId={}, owned={}", 
                             comment.getCommentId(), comment.getPostId(), requesterId, owned);
                    return owned;
                })
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        
        log.info("Returning {} comments for author: {}", comments.size(), requesterId);
        return comments;
    }

    @Override
    @Transactional
    public CommentResponse approveComment(Long commentId) {
        Comment comment = findById(commentId);
        comment.setStatus(CommentStatus.APPROVED);
        return mapToResponse(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public CommentResponse rejectComment(Long commentId) {
        Comment comment = findById(commentId);
        comment.setStatus(CommentStatus.REJECTED);
        return mapToResponse(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long requesterId, String requesterRole) {
        Comment comment = findById(commentId);

        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            ensureOwner(comment, requesterId);
        }

        // Hard delete the comment and its replies
        List<Comment> replies = commentRepository.findByParentIdOrderByCreatedAtAsc(commentId);
        if (replies != null && !replies.isEmpty()) {
            commentRepository.deleteAll(replies);
        }
        commentRepository.delete(comment);
        log.info("Permanently deleted commentId={} and its {} replies", commentId, replies != null ? replies.size() : 0);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long commentId, UpdateCommentRequest request, Long requesterId, String requesterRole) {
        Comment comment = findById(commentId);

        if (!"ADMIN".equalsIgnoreCase(requesterRole)) {
            ensureOwner(comment, requesterId);
            ensureWithinTimeWindow(comment);
        }

        comment.setContent(HtmlSanitizer.sanitize(request.getContent()));
        return mapToResponse(commentRepository.save(comment));
    }

    private void ensureOwner(Comment comment, Long requesterId) {
        if (!comment.getAuthorId().equals(requesterId)) {
            throw new CustomException("You can only manage your own comments", HttpStatus.FORBIDDEN);
        }
    }

    private void ensureWithinTimeWindow(Comment comment) {
        if (comment.getCreatedAt().plusHours(24).isBefore(LocalDateTime.now())) {
            throw new CustomException("The 24-hour window for editing/deleting has passed", HttpStatus.BAD_REQUEST);
        }
    }

    /**
     * Moderate a comment: APPROVE, REJECT, or DELETE.
     * <ul>
     *   <li>ADMIN: can moderate any comment on any post.</li>
     *   <li>AUTHOR: can moderate comments only on their own posts.</li>
     *   <li>READER: forbidden.</li>
     * </ul>
     */
    @Override
    @Transactional
    public CommentResponse moderateComment(Long commentId, ModerationRequest request,
                                           Long requesterId, String requesterRole) {
        Comment comment = findById(commentId);
        String action = request.getAction() == null ? "" : request.getAction().trim().toUpperCase(Locale.ROOT);

        if ("ADMIN".equalsIgnoreCase(requesterRole)) {
            // Admin can moderate anything — no ownership check needed
        } else if ("AUTHOR".equalsIgnoreCase(requesterRole)) {
            // Author can only moderate comments on their own posts
            ensurePostAuthorOrAdmin(comment.getPostId(), requesterId);
        } else {
            throw new CustomException("You do not have permission to moderate comments", HttpStatus.FORBIDDEN);
        }

        switch (action) {
            case "APPROVE" -> {
                comment.setStatus(CommentStatus.APPROVED);
            }
            case "REJECT" -> {
                comment.setStatus(CommentStatus.REJECTED);
            }
            case "DELETE" -> {
                if (comment.getStatus() == CommentStatus.DELETED) {
                    throw new CustomException("Comment has already been deleted", HttpStatus.BAD_REQUEST);
                }
                LocalDateTime now = LocalDateTime.now();
                comment.setStatus(CommentStatus.DELETED);
                comment.setDeletedAt(now);
                commentRepository.save(comment);
                commentRepository.bulkSoftDeleteChildrenByParentId(commentId, now);
                return mapToResponse(comment);
            }
            default -> throw new CustomException("Unknown moderation action: " + request.getAction(), HttpStatus.BAD_REQUEST);
        }

        return mapToResponse(commentRepository.save(comment));
    }

    /**
     * Verifies that the requesting user is the author of the post the comment belongs to.
     * Throws 403 if not.
     */
    private void ensurePostAuthorOrAdmin(Long postId, Long requesterId) {
        try {
            Map<String, Object> post = postClient.getPostById(postId);
            Long postAuthorId = Long.valueOf(post.get("authorId").toString());
            if (!postAuthorId.equals(requesterId)) {
                throw new CustomException(
                    "You can only moderate comments on your own posts", HttpStatus.FORBIDDEN);
            }
        } catch (CustomException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify post ownership for postId={}: {}", postId, e.getMessage());
            throw new CustomException("Unable to verify post ownership", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private boolean isOwnedByAuthor(Long postId, Long requesterId, Map<Long, Long> postAuthorIds) {
        log.info("Checking ownership for postId: {} by requesterId: {}", postId, requesterId);
        if (postAuthorIds.containsKey(postId)) {
            boolean owned = Objects.equals(postAuthorIds.get(postId), requesterId);
            log.info("PostId: {} author cached: {}. Owned: {}", postId, postAuthorIds.get(postId), owned);
            return owned;
        }

        try {
            Map<String, Object> post = postClient.getPostById(postId);
            log.info("PostClient returned for postId {}: {}", postId, post);
            if (post == null || !post.containsKey("authorId")) {
                log.warn("Post information for postId {} is incomplete or null", postId);
                return false;
            }
            Long postAuthorId = Long.valueOf(post.get("authorId").toString());
            postAuthorIds.put(postId, postAuthorId);
            boolean owned = Objects.equals(postAuthorId, requesterId);
            log.info("PostId: {} fetched author: {}. Owned: {}", postId, postAuthorId, owned);
            return owned;
        } catch (Exception e) {
            log.error("Failed to resolve author ownership for postId={} due to: {}", postId, e.getMessage(), e);
            return false;
        }
    }

    private void validateCommentRequest(CommentRequest request) {
        if (request.getParentId() == null) {
            return;
        }

        Comment parent = findById(request.getParentId());
        if (!Objects.equals(parent.getPostId(), request.getPostId())) {
            throw new CustomException("Reply must belong to the same post", HttpStatus.BAD_REQUEST);
        }
        if (parent.getParentId() != null) {
            throw new CustomException("Replies can only be added to top-level comments", HttpStatus.BAD_REQUEST);
        }
        if (parent.getStatus() == CommentStatus.DELETED || parent.getStatus() == CommentStatus.REJECTED) {
            throw new CustomException("Replies cannot be added to moderated comments", HttpStatus.BAD_REQUEST);
        }
    }


    @Override
    @Transactional
    public CommentResponse likeComment(Long commentId, Long requesterId) {
        Comment comment = findById(commentId);
        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, requesterId)) {
            throw new CustomException("Comment already liked", HttpStatus.BAD_REQUEST);
        }
        commentLikeRepository.save(CommentLike.builder()
                .commentId(commentId)
                .userId(requesterId)
                .build());
        comment.setLikesCount(comment.getLikesCount() + 1);
        return mapToResponse(commentRepository.save(comment));
    }

    @Override
    @Transactional
    public CommentResponse unlikeComment(Long commentId, Long requesterId) {
        Comment comment = findById(commentId);
        if (commentLikeRepository.existsByCommentIdAndUserId(commentId, requesterId)) {
            commentLikeRepository.deleteByCommentIdAndUserId(commentId, requesterId);
            comment.setLikesCount(Math.max(0L, comment.getLikesCount() - 1));
            return mapToResponse(commentRepository.save(comment));
        }
        return mapToResponse(comment);
    }

    private Comment findById(Long id) {
        return commentRepository.findById(id)
                .orElseThrow(() -> new CustomException("Comment not found", HttpStatus.NOT_FOUND));
    }

    @Override
    public long getCommentCountByPost(Long postId) {
        return commentRepository.countByPostIdAndStatus(postId, CommentStatus.APPROVED);
    }



    private CommentResponse mapToResponse(Comment comment) {
        return CommentResponse.builder()
                .commentId(comment.getCommentId())
                .postId(comment.getPostId())
                .authorId(comment.getAuthorId())
                .parentId(comment.getParentId())
                .content(comment.getContent())
                .status(comment.getStatus())
                .likesCount(comment.getLikesCount())
                .deletedAt(comment.getDeletedAt())
                .createdAt(comment.getCreatedAt())
                .updatedAt(comment.getUpdatedAt())
                .build();
    }
}
