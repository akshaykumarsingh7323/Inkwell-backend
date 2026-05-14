package com.inkwell.post.event;

import com.inkwell.post.config.RabbitMQConfig;
import com.inkwell.post.dto.NotificationEvent;
import com.inkwell.post.dto.PostPublishedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishPostPublishedEvent(PostPublishedEvent event) {
        try {
            log.info("Publishing post published event for postId: {}", event.getPostId());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.POST_EXCHANGE,
                    RabbitMQConfig.POST_PUBLISHED_ROUTING_KEY,
                    event
            );
            
            // Also notify newsletter-service
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NEWSLETTER_EXCHANGE,
                    RabbitMQConfig.NEWSLETTER_POST_KEY,
                    event
            );
            
            log.info("Successfully sent post published event to RabbitMQ for postId: {}", event.getPostId());
        } catch (Exception e) {
            log.error("Failed to publish post published event (RabbitMQ might be down): {}", e.getMessage());
        }
    }

    public void publishNotificationEvent(NotificationEvent event) {
        try {
            log.info("Publishing notification event for userId: {}", event.getUserId());
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.NOTIFICATION_EXCHANGE,
                    RabbitMQConfig.NOTIFICATION_ROUTING_KEY,
                    event
            );
        } catch (Exception e) {
            log.error("Failed to publish notification event (RabbitMQ might be down): {}", e.getMessage());
        }
    }
}
