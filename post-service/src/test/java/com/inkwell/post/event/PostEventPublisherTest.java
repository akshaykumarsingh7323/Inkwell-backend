package com.inkwell.post.event;

import com.inkwell.post.config.RabbitMQConfig;
import com.inkwell.post.dto.NotificationEvent;
import com.inkwell.post.dto.PostPublishedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostEventPublisherTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private PostEventPublisher postEventPublisher;

    @Test
    void publishPostPublishedEventShouldCallRabbitTemplate() {
        PostPublishedEvent event = PostPublishedEvent.builder()
                .postId(1L)
                .title("Title")
                .authorId(10L)
                .build();

        postEventPublisher.publishPostPublishedEvent(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.POST_EXCHANGE),
                eq(RabbitMQConfig.POST_PUBLISHED_ROUTING_KEY),
                eq(event)
        );
    }

    @Test
    void publishPostPublishedEventShouldHandleException() {
        PostPublishedEvent event = PostPublishedEvent.builder().postId(1L).build();
        doThrow(new RuntimeException("RabbitMQ down")).when(rabbitTemplate)
                .convertAndSend(any(), any(), any(Object.class));

        // Should not throw exception
        postEventPublisher.publishPostPublishedEvent(event);

        verify(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));
    }

    @Test
    void publishNotificationEventShouldCallRabbitTemplate() {
        NotificationEvent event = NotificationEvent.builder()
                .userId(10L)
                .type("LIKE")
                .metadata(Map.of("actorId", "99"))
                .build();

        postEventPublisher.publishNotificationEvent(event);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMQConfig.NOTIFICATION_EXCHANGE),
                eq(RabbitMQConfig.NOTIFICATION_ROUTING_KEY),
                eq(event)
        );
    }

    @Test
    void publishNotificationEventShouldHandleException() {
        NotificationEvent event = NotificationEvent.builder().userId(10L).build();
        doThrow(new RuntimeException("RabbitMQ down")).when(rabbitTemplate)
                .convertAndSend(any(), any(), any(Object.class));

        // Should not throw exception
        postEventPublisher.publishNotificationEvent(event);

        verify(rabbitTemplate).convertAndSend(any(), any(), any(Object.class));
    }
}
