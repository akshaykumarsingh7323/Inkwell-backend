package com.inkwell.comment.event;

import com.inkwell.comment.config.RabbitMQConfig;
import com.inkwell.comment.dto.NotificationEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class CommentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishNotificationEvent(NotificationEvent event) {
        try {
            log.info("Publishing notification event of type: {} for userId: {}", event.getType(), event.getUserId());
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
