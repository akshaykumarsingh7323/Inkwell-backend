package com.inkwell.notification.event;

import com.inkwell.notification.dto.NotificationEvent;
import com.inkwell.notification.dto.NotificationRequest;
import com.inkwell.notification.service.NotificationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener notificationEventListener;

    @Test
    void handleNotificationEvent_ForCommentReply_ShouldBuildCommentNotification() {
        NotificationEvent event = NotificationEvent.builder()
                .type("COMMENT_REPLY")
                .userId(5L)
                .message("Reply")
                .metadata(Map.of("actorId", "2", "commentId", "9", "postSlug", "my-post"))
                .build();

        notificationEventListener.handleNotificationEvent(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        NotificationRequest request = captor.getValue();
        assertEquals(5L, request.getRecipientId());
        assertEquals(2L, request.getActorId());
        assertEquals(9L, request.getRelatedId());
        assertEquals("COMMENT", request.getRelatedType());
        assertEquals("Response to your Comment", request.getTitle());
        assertEquals("my-post", request.getRelatedSlug());
    }

    @Test
    void handleNotificationEvent_ForLike_ShouldBuildPostNotification() {
        NotificationEvent event = NotificationEvent.builder()
                .type("LIKE")
                .userId(5L)
                .message("Liked")
                .metadata(Map.of("postId", "11"))
                .build();

        notificationEventListener.handleNotificationEvent(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertEquals("Post Liked", captor.getValue().getTitle());
        assertEquals(11L, captor.getValue().getRelatedId());
        assertEquals("POST", captor.getValue().getRelatedType());
    }

    @Test
    void handleNotificationEvent_WithNullMetadata_ShouldStillProcess() {
        NotificationEvent event = NotificationEvent.builder()
                .type("ADMIN_BROADCAST")
                .userId(5L)
                .message("Global msg")
                .metadata(null)
                .build();

        notificationEventListener.handleNotificationEvent(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertEquals("Notification", captor.getValue().getTitle());
        assertEquals(5L, captor.getValue().getRecipientId());
    }

    @Test
    void handleNotificationEvent_ForNewComment_ShouldBuildCommentNotification() {
        NotificationEvent event = NotificationEvent.builder()
                .type("NEW_COMMENT")
                .userId(5L)
                .message("New Comment")
                .metadata(Map.of("commentId", "123"))
                .build();

        notificationEventListener.handleNotificationEvent(event);

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationService).send(captor.capture());
        assertEquals("New Comment Received", captor.getValue().getTitle());
        assertEquals(123L, captor.getValue().getRelatedId());
        assertEquals("COMMENT", captor.getValue().getRelatedType());
    }

    @Test
    void handleNotificationEvent_WithInvalidType_ShouldCatchException() {
        NotificationEvent event = NotificationEvent.builder()
                .type("INVALID_TYPE")
                .userId(5L)
                .build();

        notificationEventListener.handleNotificationEvent(event);
        // Should catch IllegalArgumentException from NotificationType.valueOf
    }

    @Test
    void handleNotificationEvent_WhenSendFails_ShouldSwallowException() {
        NotificationEvent event = NotificationEvent.builder()
                .type("NEW_COMMENT")
                .userId(5L)
                .message("Comment")
                .build();
        doThrow(new RuntimeException("boom")).when(notificationService).send(any(NotificationRequest.class));

        notificationEventListener.handleNotificationEvent(event);
    }
}
