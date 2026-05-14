package com.inkwell.newsletter.event;

import com.inkwell.newsletter.config.RabbitMQConfig;
import com.inkwell.newsletter.service.EmailService;
import com.inkwell.newsletter.service.NewsletterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class NewsletterEventListener {

    private final EmailService emailService;
    private final NewsletterService newsletterService;

    /**
     * Consumes raw post publication event from post-service.
     */
    @RabbitListener(queues = RabbitMQConfig.NEWSLETTER_POST_QUEUE)
    public void handleRawPostPublished(com.inkwell.newsletter.dto.PostPublishedEvent event) {
        log.info("Received raw post published event from post-service: {}", event.getPostId());
        String postUrl = "http://localhost:4200/post/" + event.getSlug();
        newsletterService.sendPostNotification(event.getTitle(), postUrl, event.getAuthorId());
    }

    /**
     * Consumes individual email task for a post notification.
     */
    @RabbitListener(queues = RabbitMQConfig.NEWSLETTER_QUEUE)
    public void handleIndividualEmail(Map<String, Object> data) {
        log.info("Processing individual email task for: {}", data.get("email"));
        String template = (String) data.getOrDefault("template", "campaign");
        String subject = (String) data.getOrDefault("subject", "InkWell Update");
        
        emailService.sendHtmlEmail(
                (String) data.get("email"),
                subject,
                template,
                data
        );
    }

    /**
     * Consumes confirmation and welcome email tasks.
     */
    @RabbitListener(queues = RabbitMQConfig.NEWSLETTER_CONFIRMATION_QUEUE)
    public void handleConfirmationAndWelcome(Map<String, Object> data) {
        log.info("Processing confirmation/welcome email for: {}", data.get("email"));
        String template = (String) data.getOrDefault("template", "confirmation");
        String subject = (String) data.getOrDefault("subject", "Confirm your subscription");
        
        emailService.sendHtmlEmail(
                (String) data.get("email"),
                subject,
                template,
                data
        );
    }
}
