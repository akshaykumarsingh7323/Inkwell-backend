package com.inkwell.newsletter.controller;

import com.inkwell.newsletter.dto.CampaignRequest;
import com.inkwell.newsletter.dto.NewsletterRequest;
import com.inkwell.newsletter.dto.PreferenceRequest;
import com.inkwell.newsletter.dto.ResendConfirmationRequest;
import com.inkwell.newsletter.dto.SubscribeRequest;
import com.inkwell.newsletter.entity.Subscriber;
import com.inkwell.newsletter.exception.CustomException;
import com.inkwell.newsletter.service.NewsletterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NewsletterControllerTest {

    @Mock
    private NewsletterService newsletterService;

    @InjectMocks
    private NewsletterController newsletterController;

    @Test
    void subscribe_ShouldDelegate() {
        SubscribeRequest request = SubscribeRequest.builder().email("john@example.com").followedAuthorId(1L).build();

        assertEquals("Subscription request received. Please check your email to confirm.",
                newsletterController.subscribe(request).getBody());
        verify(newsletterService).subscribe(request);
    }

    @Test
    void confirm_ShouldDelegate() {
        assertEquals("Subscription confirmed successfully!", newsletterController.confirm("token").getBody());
        verify(newsletterService).confirmSubscription("token");
    }

    @Test
    void unsubscribe_ShouldDelegate() {
        assertEquals("You have been unsubscribed successfully.", newsletterController.unsubscribe("token").getBody());
        verify(newsletterService).unsubscribe("token");
    }

    @Test
    void getAllSubscribers_AsAuthor_ShouldUseHeaderAuthorIdWhenRequestParamMissing() {
        List<Subscriber> subscribers = List.of(Subscriber.builder().email("john@example.com").build());
        when(newsletterService.getAllSubscribers(7L)).thenReturn(subscribers);

        assertEquals(subscribers, newsletterController.getAllSubscribers(null, "7", "AUTHOR").getBody());
    }

    @Test
    void getAllSubscribers_WhenForbidden_ShouldThrow() {
        CustomException exception = assertThrows(CustomException.class,
                () -> newsletterController.getAllSubscribers(null, "7", "READER"));

        assertEquals(HttpStatus.FORBIDDEN, exception.getStatus());
    }

    @Test
    void sendNewsletter_AsAdmin_ShouldDelegate() {
        NewsletterRequest request = new NewsletterRequest();
        request.setSubject("Subject");
        request.setContent("Content");

        assertEquals("Newsletter sent to all active subscribers.", newsletterController.sendNewsletter(request, "ADMIN").getBody());
        verify(newsletterService).sendNewsletter(request);
    }

    @Test
    void notifyNewPost_ShouldDelegate() {
        assertEquals("Post notification sent to active subscribers.",
                newsletterController.notifyNewPost("Title", "https://example.com/post", 3L).getBody());
        verify(newsletterService).sendPostNotification("Title", "https://example.com/post", 3L);
    }

    @Test
    void updatePreferences_ShouldDelegate() {
        PreferenceRequest request = new PreferenceRequest();

        assertEquals("Preferences updated successfully.", newsletterController.updatePreferences(5L, request).getBody());
        verify(newsletterService).updatePreferences(5L, request);
    }

    @Test
    void getCount_AsAdmin_ShouldReturnCount() {
        when(newsletterService.getSubscriberCount(9L)).thenReturn(12L);

        assertEquals(12L, newsletterController.getCount(9L, "7", "ADMIN").getBody());
    }

    @Test
    void sendCampaign_AsAuthor_ShouldUseHeaderUserId() {
        CampaignRequest request = new CampaignRequest();
        request.setSubject("Subject");
        request.setContent("Content");

        assertEquals("Campaign dispatched successfully.", newsletterController.sendCampaign(request, "7", "AUTHOR").getBody());
        verify(newsletterService).sendCampaign(request, 7L);
    }

    @Test
    void resendConfirmation_ShouldDelegate() {
        ResendConfirmationRequest request = new ResendConfirmationRequest();
        request.setEmail("john@example.com");

        assertEquals("Confirmation email resent. Please check your inbox.", newsletterController.resendConfirmation(request).getBody());
        verify(newsletterService).resendConfirmation(request);
    }

    @Test
    void getSubscriberByEmail_ShouldReturnSubscriber() {
        Subscriber subscriber = Subscriber.builder().email("john@example.com").build();
        when(newsletterService.getSubscriberByEmail("john@example.com", 1L)).thenReturn(subscriber);

        assertEquals(subscriber, newsletterController.getSubscriberByEmail("john@example.com", 1L).getBody());
    }

    @Test
    void unsubscribeByEmail_ShouldDelegate() {
        assertEquals("Unsubscribed successfully.",
                newsletterController.unsubscribeByEmail("john@example.com", 1L).getBody());
        verify(newsletterService).unsubscribeByEmail("john@example.com", 1L);
    }
    @Test
    void getAllSubscribers_AsAdmin_WithAuthorId_ShouldUseAuthorId() {
        List<Subscriber> subscribers = List.of(Subscriber.builder().email("john@example.com").build());
        when(newsletterService.getAllSubscribers(10L)).thenReturn(subscribers);

        assertEquals(subscribers, newsletterController.getAllSubscribers(10L, "7", "ADMIN").getBody());
    }

    @Test
    void sendNewsletter_AsNonAdmin_ShouldThrow() {
        assertThrows(CustomException.class, () -> newsletterController.sendNewsletter(new NewsletterRequest(), "AUTHOR"));
    }

    @Test
    void sendCampaign_AsAdmin_ShouldUseNullAuthorId() {
        CampaignRequest request = new CampaignRequest();
        request.setSubject("Subject");
        request.setContent("Content");

        assertEquals("Campaign dispatched successfully.", newsletterController.sendCampaign(request, "7", "ADMIN").getBody());
        verify(newsletterService).sendCampaign(request, null);
    }

    @Test
    void ensureAdminOrAuthor_WhenNeither_ShouldThrow() {
        assertThrows(CustomException.class, () -> newsletterController.getCount(null, "7", "READER"));
    }

    @Test
    void getAnalytics_AsAuthor_ShouldReturnAnalytics() {
        com.inkwell.newsletter.dto.NewsletterAnalytics analytics = com.inkwell.newsletter.dto.NewsletterAnalytics.builder().build();
        when(newsletterService.getAnalytics(7L)).thenReturn(analytics);

        assertEquals(analytics, newsletterController.getAnalytics(null, "7", "AUTHOR").getBody());
        verify(newsletterService).getAnalytics(7L);
    }
}
