package com.inkwell.notification.event;

import com.inkwell.notification.config.RabbitMQConfig;
import com.inkwell.notification.dto.NotificationEvent;
import com.inkwell.notification.dto.NotificationRequest;
import com.inkwell.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Service;

import com.inkwell.notification.enums.NotificationType;
import com.inkwell.notification.dto.PostPublishedEvent;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @RabbitListener(queues = RabbitMQConfig.NOTIFICATION_QUEUE)
    public void handleNotificationEvent(NotificationEvent event) {
        log.info("Received notification event of type: {} for userId: {}", event.getType(), event.getUserId());
        try {
            Long actorId = event.getMetadata() != null && event.getMetadata().containsKey("actorId") 
                    ? Long.parseLong(event.getMetadata().get("actorId")) 
                    : null;

            Long relatedId = null;
            String relatedType = "POST";
            
            if (event.getMetadata() != null) {
                if (event.getType().contains("COMMENT")) {
                    relatedId = event.getMetadata().containsKey("commentId") 
                            ? Long.parseLong(event.getMetadata().get("commentId")) 
                            : null;
                    relatedType = "COMMENT";
                } else if (event.getMetadata().containsKey("postId")) {
                    relatedId = Long.parseLong(event.getMetadata().get("postId"));
                }
            }

            NotificationRequest request = NotificationRequest.builder()
                    .recipientId(event.getUserId())
                    .actorId(actorId)
                    .type(NotificationType.valueOf(event.getType()))
                    .title(getNotificationTitle(event.getType()))
                    .message(event.getMessage())
                    .relatedId(relatedId)
                    .relatedSlug(event.getMetadata() != null ? event.getMetadata().get("postSlug") : null)
                    .relatedType(relatedType)
                    .build();
            notificationService.send(request);
        } catch (Exception e) {
            log.error("Error processing notification event: {}", e.getMessage(), e);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.POST_PUBLISHED_QUEUE)
    public void handlePostPublishedEvent(PostPublishedEvent event) {
        log.info("Received post published event for postId: {}, title: {}", event.getPostId(), event.getTitle());
        try {
            String title = "New Post Submission";
            String message = "A new post titled '" + event.getTitle() + "' has been published and needs review.";
            
            log.debug("Calling notifyAdmins for post: {}", event.getPostId());
            notificationService.notifyAdmins(title, message, event.getPostId(), event.getSlug());
            log.info("Successfully processed post published event for postId: {}", event.getPostId());
        } catch (Exception e) {
            log.error("Error processing post published event for postId {}: {}", event.getPostId(), e.getMessage(), e);
        }
    }

    private String getNotificationTitle(String type) {
        switch (type) {
            case "LIKE": return "Post Liked";
            case "NEW_COMMENT": return "New Comment Received";
            case "COMMENT_REPLY": return "Response to your Comment";
            default: return "Notification";
        }
    }
}
