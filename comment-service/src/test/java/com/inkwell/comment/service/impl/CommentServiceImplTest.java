package com.inkwell.comment.service.impl;

import com.inkwell.comment.client.AuthClient;
import com.inkwell.comment.client.PostClient;
import com.inkwell.comment.dto.CommentRequest;
import com.inkwell.comment.dto.CommentResponse;
import com.inkwell.comment.dto.ModerationRequest;
import com.inkwell.comment.dto.NotificationEvent;
import com.inkwell.comment.dto.UpdateCommentRequest;
import com.inkwell.comment.entity.Comment;
import com.inkwell.comment.entity.CommentStatus;
import com.inkwell.comment.event.CommentEventPublisher;
import com.inkwell.comment.exception.CustomException;
import com.inkwell.comment.repository.CommentLikeRepository;
import com.inkwell.comment.repository.CommentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceImplTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private CommentLikeRepository commentLikeRepository;

    @Mock
    private PostClient postClient;

    @Mock
    private AuthClient authClient;

    @Mock
    private CommentEventPublisher commentEventPublisher;

    @InjectMocks
    private CommentServiceImpl commentService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(commentService, "autoApprove", false);
    }

    @Test
    void createComment_ShouldCreatePendingComment_AndNotifyPostAuthor() {
        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content("Test content")
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content("Test content")
                .status(CommentStatus.PENDING)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(3L, "Post Title", "post-title"));
        when(authClient.getUserById(2L)).thenReturn(Map.of("username", "actor"));

        CommentResponse response = commentService.createComment(request, 2L);

        assertNotNull(response);
        assertEquals(CommentStatus.PENDING, response.getStatus());
        verify(commentEventPublisher).publishNotificationEvent(any(NotificationEvent.class));
    }

    @Test
    void createComment_ShouldCreateApprovedComment_WhenAutoApproveEnabled() {
        ReflectionTestUtils.setField(commentService, "autoApprove", true);

        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content("Approved content")
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content("Approved content")
                .status(CommentStatus.APPROVED)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(3L, "Post Title", "post-title"));
        when(authClient.getUserById(2L)).thenReturn(Map.of("username", "actor"));

        CommentResponse response = commentService.createComment(request, 2L);

        assertEquals(CommentStatus.APPROVED, response.getStatus());
        verify(commentEventPublisher).publishNotificationEvent(any(NotificationEvent.class));
    }

    @Test
    void createComment_ShouldSkipNotifications_WhenPostLookupReturnsNull() {
        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content("Test content")
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content("Test content")
                .status(CommentStatus.PENDING)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(null);

        CommentResponse response = commentService.createComment(request, 2L);

        assertNotNull(response);
        verify(commentEventPublisher, never()).publishNotificationEvent(any(NotificationEvent.class));
    }

    @Test
    void createComment_ShouldSkipPostAuthorNotification_WhenCommentAuthorOwnsPost() {
        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content("Own post comment")
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content("Own post comment")
                .status(CommentStatus.PENDING)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(2L, "Post Title", "post-title"));

        commentService.createComment(request, 2L);

        verifyNoInteractions(authClient);
        verify(commentEventPublisher, never()).publishNotificationEvent(any(NotificationEvent.class));
    }

    @Test
    void createComment_ShouldFallbackToSomeone_WhenActorLookupFails() {
        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content("Test content")
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content("Test content")
                .status(CommentStatus.PENDING)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(3L, "Post Title", "post-title"));
        when(authClient.getUserById(2L)).thenThrow(new RuntimeException("auth unavailable"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);

        commentService.createComment(request, 2L);

        verify(commentEventPublisher).publishNotificationEvent(captor.capture());
        assertEquals("Someone", captor.getValue().getMetadata().get("actorName"));
        assertTrue(captor.getValue().getMessage().contains("Someone commented on your post"));
    }

    @Test
    void createComment_ShouldTruncateLongNotificationMessage() {
        String longContent = "x".repeat(600);
        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content(longContent)
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content(longContent)
                .status(CommentStatus.PENDING)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(3L, "Post Title", "post-title"));
        when(authClient.getUserById(2L)).thenReturn(Map.of("username", "actor"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);

        commentService.createComment(request, 2L);

        verify(commentEventPublisher).publishNotificationEvent(captor.capture());
        assertEquals(500, captor.getValue().getMessage().length());
        assertTrue(captor.getValue().getMessage().endsWith("..."));
    }

    @Test
    void createComment_ShouldNotifyParentAuthor_WhenApprovedReplyIsCreated() {
        ReflectionTestUtils.setField(commentService, "autoApprove", true);

        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .parentId(10L)
                .content("Reply content")
                .build();

        Comment parent = Comment.builder()
                .commentId(10L)
                .postId(1L)
                .authorId(4L)
                .status(CommentStatus.APPROVED)
                .build();

        Comment savedComment = Comment.builder()
                .commentId(11L)
                .postId(1L)
                .parentId(10L)
                .authorId(2L)
                .content("Reply content")
                .status(CommentStatus.APPROVED)
                .build();

        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(3L, "Post Title", "post-title"));
        when(authClient.getUserById(2L)).thenReturn(Map.of("username", "actor"));

        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);

        commentService.createComment(request, 2L);

        verify(commentEventPublisher, times(2)).publishNotificationEvent(captor.capture());
        List<NotificationEvent> events = captor.getAllValues();
        assertEquals("NEW_COMMENT", events.get(0).getType());
        assertEquals("COMMENT_REPLY", events.get(1).getType());
        assertEquals(4L, events.get(1).getUserId());
    }

    @Test
    void createComment_ShouldNotNotifyParentAuthor_WhenReplyAuthorMatchesParentAuthor() {
        ReflectionTestUtils.setField(commentService, "autoApprove", true);

        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .parentId(10L)
                .content("Reply content")
                .build();

        Comment parent = Comment.builder()
                .commentId(10L)
                .postId(1L)
                .authorId(2L)
                .status(CommentStatus.APPROVED)
                .build();

        Comment savedComment = Comment.builder()
                .commentId(11L)
                .postId(1L)
                .parentId(10L)
                .authorId(2L)
                .content("Reply content")
                .status(CommentStatus.APPROVED)
                .build();

        when(commentRepository.findById(10L)).thenReturn(Optional.of(parent));
        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(3L, "Post Title", "post-title"));
        when(authClient.getUserById(2L)).thenReturn(Map.of("username", "actor"));

        commentService.createComment(request, 2L);

        verify(commentEventPublisher, times(1)).publishNotificationEvent(any(NotificationEvent.class));
    }

    @Test
    void createComment_ShouldSwallowNotificationErrors() {
        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content("Test content")
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content("Test content")
                .status(CommentStatus.PENDING)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenThrow(new RuntimeException("downstream failure"));

        CommentResponse response = commentService.createComment(request, 2L);

        assertNotNull(response);
    }

    @Test
    void getApprovedCommentsByPost_ShouldMapApprovedComments() {
        Comment comment = Comment.builder()
                .commentId(1L)
                .postId(9L)
                .authorId(2L)
                .content("Approved")
                .status(CommentStatus.APPROVED)
                .build();

        when(commentRepository.findByPostIdAndStatusOrderByCreatedAtAsc(9L, CommentStatus.APPROVED))
                .thenReturn(List.of(comment));

        List<CommentResponse> result = commentService.getApprovedCommentsByPost(9L);

        assertEquals(1, result.size());
        assertEquals(1L, result.get(0).getCommentId());
    }

    @Test
    void getPendingComments_ShouldReturnPendingAndApprovedComments() {
        Comment pending = Comment.builder().commentId(1L).status(CommentStatus.PENDING).build();
        Comment approved = Comment.builder().commentId(2L).status(CommentStatus.APPROVED).build();
        when(commentRepository.findByStatusInOrderByCreatedAtDesc(anyList())).thenReturn(List.of(pending, approved));

        List<CommentResponse> result = commentService.getPendingComments();

        assertEquals(2, result.size());
    }

    @Test
    void getPendingCommentsForRequester_AsAdmin_ShouldReturnAllPendingComments() {
        Comment comment = Comment.builder().commentId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findByStatusInOrderByCreatedAtDesc(anyList())).thenReturn(List.of(comment));

        List<CommentResponse> result = commentService.getPendingCommentsForRequester(99L, "ADMIN");

        assertEquals(1, result.size());
        verifyNoInteractions(postClient);
    }

    @Test
    void getPendingCommentsForRequester_AsAuthor_ShouldUseCachedPostOwnership() {
        Comment first = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        Comment second = Comment.builder().commentId(2L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findByStatusInOrderByCreatedAtDesc(anyList())).thenReturn(List.of(first, second));
        when(postClient.getPostById(1L)).thenReturn(post(10L, "Post", "post"));

        List<CommentResponse> result = commentService.getPendingCommentsForRequester(10L, "AUTHOR");

        assertEquals(2, result.size());
        verify(postClient, times(1)).getPostById(1L); // Verify cache hit
    }

    @Test
    void getPendingCommentsForRequester_AsAuthor_ShouldFilterOutUnownedComments_WhenPostMissingAuthor() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findByStatusInOrderByCreatedAtDesc(anyList())).thenReturn(List.of(comment));
        when(postClient.getPostById(1L)).thenReturn(new HashMap<>());

        List<CommentResponse> result = commentService.getPendingCommentsForRequester(10L, "AUTHOR");

        assertTrue(result.isEmpty());
    }

    @Test
    void getPendingCommentsForRequester_AsAuthor_ShouldFilterOutUnownedComments_WhenLookupFails() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findByStatusInOrderByCreatedAtDesc(anyList())).thenReturn(List.of(comment));
        when(postClient.getPostById(1L)).thenThrow(new RuntimeException("post unavailable"));

        List<CommentResponse> result = commentService.getPendingCommentsForRequester(10L, "AUTHOR");

        assertTrue(result.isEmpty());
    }

    @Test
    void getPendingCommentsForRequester_AsReader_ShouldThrowForbidden() {
        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.getPendingCommentsForRequester(10L, "READER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void approveComment_ShouldSetApprovedStatus() {
        Comment comment = Comment.builder().commentId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        CommentResponse response = commentService.approveComment(1L);

        assertEquals(CommentStatus.APPROVED, response.getStatus());
    }

    @Test
    void rejectComment_ShouldSetRejectedStatus() {
        Comment comment = Comment.builder().commentId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        CommentResponse response = commentService.rejectComment(1L);

        assertEquals(CommentStatus.REJECTED, response.getStatus());
    }

    @Test
    void deleteComment_AsOwner_WithReplies_ShouldHardDeleteRepliesAndComment() {
        Comment comment = Comment.builder()
                .commentId(1L)
                .authorId(2L)
                .createdAt(LocalDateTime.now())
                .build();
        Comment reply = Comment.builder().commentId(3L).parentId(1L).build();

        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(1L)).thenReturn(List.of(reply));

        commentService.deleteComment(1L, 2L, "READER");

        verify(commentRepository).deleteAll(List.of(reply));
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_AsAdmin_WithNullReplies_ShouldDeleteCommentOnly() {
        Comment comment = Comment.builder().commentId(1L).authorId(2L).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.findByParentIdOrderByCreatedAtAsc(1L)).thenReturn(null);

        commentService.deleteComment(1L, 999L, "ADMIN");

        verify(commentRepository, never()).deleteAll(anyList());
        verify(commentRepository).delete(comment);
    }

    @Test
    void deleteComment_AsNonOwner_ShouldThrowForbidden() {
        Comment comment = Comment.builder().commentId(1L).authorId(2L).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.deleteComment(1L, 5L, "READER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
        verify(commentRepository, never()).delete(any(Comment.class));
    }

    @Test
    void updateComment_AsOwnerWithinTimeWindow_ShouldSanitizeAndSave() {
        Comment comment = Comment.builder()
                .commentId(1L)
                .authorId(2L)
                .content("old")
                .createdAt(LocalDateTime.now().minusHours(1))
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        UpdateCommentRequest request = UpdateCommentRequest.builder()
                .content("<script>alert(1)</script><b>Safe</b>")
                .build();

        CommentResponse response = commentService.updateComment(1L, request, 2L, "READER");

        assertEquals("<b>Safe</b>", response.getContent());
    }

    @Test
    void updateComment_AsAdmin_ShouldSkipOwnershipAndTimeChecks() {
        Comment comment = Comment.builder()
                .commentId(1L)
                .authorId(2L)
                .content("old")
                .createdAt(LocalDateTime.now().minusDays(3))
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        UpdateCommentRequest request = UpdateCommentRequest.builder().content("Admin edit").build();

        CommentResponse response = commentService.updateComment(1L, request, 99L, "ADMIN");

        assertEquals("Admin edit", response.getContent());
    }

    @Test
    void updateComment_ShouldCheckTimeWindow() {
        Comment comment = Comment.builder()
                .commentId(1L)
                .authorId(2L)
                .createdAt(LocalDateTime.now().minusHours(25))
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        UpdateCommentRequest request = new UpdateCommentRequest();
        request.setContent("New Content");

        assertThrows(CustomException.class, () ->
                commentService.updateComment(1L, request, 2L, "READER")
        );
    }

    @Test
    void moderateComment_AsAdmin_ShouldApproveComment() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        ModerationRequest request = new ModerationRequest();
        request.setAction("APPROVE");

        CommentResponse response = commentService.moderateComment(1L, request, 1L, "ADMIN");

        assertEquals(CommentStatus.APPROVED, response.getStatus());
    }

    @Test
    void moderateComment_AsAdmin_ShouldRejectComment() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);

        ModerationRequest request = new ModerationRequest();
        request.setAction("REJECT");

        CommentResponse response = commentService.moderateComment(1L, request, 1L, "ADMIN");

        assertEquals(CommentStatus.REJECTED, response.getStatus());
    }

    @Test
    void moderateComment_DeleteAction_ShouldSoftDelete() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        ModerationRequest request = new ModerationRequest();
        request.setAction("DELETE");

        CommentResponse response = commentService.moderateComment(1L, request, 1L, "ADMIN");

        assertEquals(CommentStatus.DELETED, response.getStatus());
        assertNotNull(response.getDeletedAt());
        verify(commentRepository).bulkSoftDeleteChildrenByParentId(eq(1L), any(LocalDateTime.class));
    }

    @Test
    void moderateComment_DeleteAction_WhenAlreadyDeleted_ShouldThrowBadRequest() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.DELETED).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        ModerationRequest request = new ModerationRequest();
        request.setAction("DELETE");

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.moderateComment(1L, request, 1L, "ADMIN"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void moderateComment_AsAuthor_OnOwnedPost_ShouldModerateComment() {
        Comment comment = Comment.builder().commentId(1L).postId(8L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentRepository.save(comment)).thenReturn(comment);
        when(postClient.getPostById(8L)).thenReturn(post(77L, "Post", "slug"));

        ModerationRequest request = new ModerationRequest();
        request.setAction("APPROVE");

        CommentResponse response = commentService.moderateComment(1L, request, 77L, "AUTHOR");

        assertEquals(CommentStatus.APPROVED, response.getStatus());
    }

    @Test
    void moderateComment_AsAuthor_OnDifferentPostOwner_ShouldThrowForbidden() {
        Comment comment = Comment.builder().commentId(1L).postId(8L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(postClient.getPostById(8L)).thenReturn(post(77L, "Post", "slug"));

        ModerationRequest request = new ModerationRequest();
        request.setAction("APPROVE");

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.moderateComment(1L, request, 88L, "AUTHOR"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void moderateComment_AsAuthor_WhenPostLookupFails_ShouldThrowServiceUnavailable() {
        Comment comment = Comment.builder().commentId(1L).postId(8L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(postClient.getPostById(8L)).thenThrow(new RuntimeException("post unavailable"));

        ModerationRequest request = new ModerationRequest();
        request.setAction("APPROVE");

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.moderateComment(1L, request, 88L, "AUTHOR"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.getStatus());
    }

    @Test
    void moderateComment_AsReader_ShouldThrowForbidden() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        ModerationRequest request = new ModerationRequest();
        request.setAction("APPROVE");

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.moderateComment(1L, request, 1L, "READER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void moderateComment_WithUnknownAction_ShouldThrowBadRequest() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        ModerationRequest request = new ModerationRequest();
        request.setAction("UNKNOWN");

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.moderateComment(1L, request, 1L, "ADMIN"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void moderateComment_WithNullAction_ShouldThrowBadRequest() {
        Comment comment = Comment.builder().commentId(1L).postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        ModerationRequest request = new ModerationRequest();

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.moderateComment(1L, request, 1L, "ADMIN"));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void createComment_ShouldAllowTopLevelCommentWithoutParentValidation() {
        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .content("Top level")
                .build();

        Comment savedComment = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .authorId(2L)
                .content("Top level")
                .status(CommentStatus.PENDING)
                .build();

        when(commentRepository.save(any(Comment.class))).thenReturn(savedComment);
        when(postClient.getPostById(1L)).thenReturn(post(3L, "Post Title", "post-title"));
        when(authClient.getUserById(2L)).thenReturn(Map.of("username", "actor"));

        CommentResponse response = commentService.createComment(request, 2L);

        assertNull(response.getParentId());
        verify(commentRepository, never()).findById(anyLong());
    }

    @Test
    void createComment_ShouldRejectReplyToDifferentPost() {
        Comment parent = Comment.builder()
                .commentId(1L)
                .postId(99L)
                .parentId(null)
                .status(CommentStatus.APPROVED)
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .parentId(1L)
                .content("Reply")
                .build();

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.createComment(request, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void validateCommentRequest_ShouldPreventNestedReplies() {
        Comment parent = Comment.builder().commentId(1L).postId(1L).parentId(10L).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

        CommentRequest request = new CommentRequest();
        request.setPostId(1L);
        request.setParentId(1L);
        request.setContent("Reply");

        assertThrows(CustomException.class, () -> commentService.createComment(request, 2L));
    }

    @Test
    void createComment_ShouldRejectReplyToDeletedParent() {
        Comment parent = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .status(CommentStatus.DELETED)
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .parentId(1L)
                .content("Reply")
                .build();

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.createComment(request, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void createComment_ShouldRejectReplyToRejectedParent() {
        Comment parent = Comment.builder()
                .commentId(1L)
                .postId(1L)
                .status(CommentStatus.REJECTED)
                .build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(parent));

        CommentRequest request = CommentRequest.builder()
                .postId(1L)
                .parentId(1L)
                .content("Reply")
                .build();

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.createComment(request, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
    }

    @Test
    void likeComment_ShouldIncrementCount() {
        Comment comment = Comment.builder().commentId(1L).likesCount(0L).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByCommentIdAndUserId(1L, 2L)).thenReturn(false);
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);

        commentService.likeComment(1L, 2L);

        assertEquals(1L, comment.getLikesCount());
        verify(commentLikeRepository).save(any());
    }

    @Test
    void likeComment_WhenAlreadyLiked_ShouldThrowBadRequest() {
        Comment comment = Comment.builder().commentId(1L).likesCount(1L).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByCommentIdAndUserId(1L, 2L)).thenReturn(true);

        CustomException exception = assertThrows(CustomException.class,
                () -> commentService.likeComment(1L, 2L));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatus());
        verify(commentLikeRepository, never()).save(any());
    }

    @Test
    void unlikeComment_WhenLiked_ShouldDeleteLikeAndDecrementCount() {
        Comment comment = Comment.builder().commentId(1L).likesCount(3L).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByCommentIdAndUserId(1L, 2L)).thenReturn(true);
        when(commentRepository.save(comment)).thenReturn(comment);

        CommentResponse response = commentService.unlikeComment(1L, 2L);

        assertEquals(2L, response.getLikesCount());
        verify(commentLikeRepository).deleteByCommentIdAndUserId(1L, 2L);
    }

    @Test
    void unlikeComment_WhenNotLiked_ShouldReturnCommentWithoutSaving() {
        Comment comment = Comment.builder().commentId(1L).likesCount(3L).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));
        when(commentLikeRepository.existsByCommentIdAndUserId(1L, 2L)).thenReturn(false);

        CommentResponse response = commentService.unlikeComment(1L, 2L);

        assertEquals(3L, response.getLikesCount());
        verify(commentRepository, never()).save(any(Comment.class));
    }

    @Test
    void getCommentCountByPost_ShouldReturnApprovedCommentCount() {
        when(commentRepository.countByPostIdAndStatus(5L, CommentStatus.APPROVED)).thenReturn(7L);

        long count = commentService.getCommentCountByPost(5L);

        assertEquals(7L, count);
    }

    @Test
    void triggerCommentNotifications_WhenAuthorIdMissing_ShouldReturn() {
        Comment comment = Comment.builder().postId(1L).authorId(2L).status(CommentStatus.APPROVED).build();
        Map<String, Object> post = new HashMap<>();
        post.put("title", "Post");
        when(postClient.getPostById(1L)).thenReturn(post);

        // This is tricky as triggerCommentNotifications is private and called from createComment
        CommentRequest request = CommentRequest.builder().postId(1L).content("Content").build();
        when(commentRepository.save(any(Comment.class))).thenReturn(comment);
        ReflectionTestUtils.setField(commentService, "autoApprove", true);

        commentService.createComment(request, 2L);
        verify(commentEventPublisher, never()).publishNotificationEvent(any());
    }

    @Test
    void moderateComment_WithNullAction_ShouldThrow() {
        Comment comment = Comment.builder().postId(1L).status(CommentStatus.PENDING).build();
        when(commentRepository.findById(1L)).thenReturn(Optional.of(comment));

        ModerationRequest request = new ModerationRequest();
        request.setAction(null);

        assertThrows(CustomException.class, () -> commentService.moderateComment(1L, request, 99L, "ADMIN"));
    }

    @Test
    void findById_WhenNotFound_ShouldThrow() {
        when(commentRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(CustomException.class, () -> commentService.approveComment(999L));
    }

    private Map<String, Object> post(Long authorId, String title, String slug) {
        Map<String, Object> post = new HashMap<>();
        post.put("authorId", authorId);
        post.put("title", title);
        post.put("slug", slug);
        return post;
    }
}
