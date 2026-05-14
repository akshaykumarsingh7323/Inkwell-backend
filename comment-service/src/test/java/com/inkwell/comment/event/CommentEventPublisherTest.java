package com.inkwell.comment.event;

import com.inkwell.comment.config.RabbitMQConfig;
import com.inkwell.comment.dto.NotificationEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private CommentEventPublisher publisher;

    @Test
    void publishNotificationEvent_ShouldCallRabbitTemplate() {
        NotificationEvent event = NotificationEvent.builder()
                .userId(1L)
                .type("TEST")
                .message("Message")
                .build();

        publisher.publishNotificationEvent(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.NOTIFICATION_EXCHANGE),
                eq(RabbitMQConfig.NOTIFICATION_ROUTING_KEY),
                eq(event)
        );
    }

    @Test
    void publishNotificationEvent_WhenRabbitThrows_ShouldCatchAndLog() {
        NotificationEvent event = new NotificationEvent();
        doThrow(new RuntimeException("Rabbit down")).when(rabbitTemplate)
                .convertAndSend(any(), any(), any(NotificationEvent.class));

        // Should not throw exception
        publisher.publishNotificationEvent(event);

        verify(rabbitTemplate).convertAndSend(any(), any(), any(NotificationEvent.class));
    }
}
