package com.inkwell.newsletter.event;

import com.inkwell.newsletter.dto.PostPublishedEvent;
import com.inkwell.newsletter.service.EmailService;
import com.inkwell.newsletter.service.NewsletterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NewsletterEventListenerTest {

    @Mock
    private NewsletterService newsletterService;

    @Mock
    private EmailService emailService;

    @InjectMocks
    private NewsletterEventListener newsletterEventListener;

    @Test
    void handleRawPostPublished_ShouldCallService() {
        PostPublishedEvent event = PostPublishedEvent.builder()
                .postId(1L)
                .authorId(9L)
                .title("Title")
                .slug("slug")
                .build();

        newsletterEventListener.handleRawPostPublished(event);

        verify(newsletterService).sendPostNotification("Title", "http://localhost:4200/post/slug", 9L);
    }

    @Test
    void handleIndividualEmail_ShouldCallEmailService() {
        Map<String, Object> data = new HashMap<>();
        data.put("email", "test@example.com");
        data.put("subject", "Test Subject");
        data.put("template", "test-template");

        newsletterEventListener.handleIndividualEmail(data);

        verify(emailService).sendHtmlEmail(eq("test@example.com"), eq("Test Subject"), eq("test-template"), eq(data));
    }

    @Test
    void handleConfirmationAndWelcome_ShouldCallEmailService() {
        Map<String, Object> data = new HashMap<>();
        data.put("email", "welcome@example.com");
        data.put("subject", "Welcome");
        data.put("template", "welcome-template");

        newsletterEventListener.handleConfirmationAndWelcome(data);

        verify(emailService).sendHtmlEmail(eq("welcome@example.com"), eq("Welcome"), eq("welcome-template"), eq(data));
    }
}
