package com.inkwell.newsletter.service.impl;

import com.inkwell.newsletter.config.RabbitMQConfig;
import com.inkwell.newsletter.dto.*;
import com.inkwell.newsletter.entity.Subscriber;
import com.inkwell.newsletter.entity.SubscriberStatus;
import com.inkwell.newsletter.exception.CustomException;
import com.inkwell.newsletter.repository.SubscriberRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NewsletterServiceImplTest {

    @Mock
    private SubscriberRepository subscriberRepository;

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private NewsletterServiceImpl newsletterService;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(newsletterService, "frontendUrl", "http://localhost:4200");
    }

    @Test
    void subscribe_NewEmail_ShouldCreateSubscriberAndSendConfirmation() {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("test@example.com");
        request.setFullName("Full Name");

        when(subscriberRepository.findByEmailAndFollowedAuthorId("test@example.com", null)).thenReturn(Optional.empty());
        when(subscriberRepository.save(any(Subscriber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        newsletterService.subscribe(request);

        verify(subscriberRepository).save(any(Subscriber.class));
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.NEWSLETTER_EXCHANGE), eq(RabbitMQConfig.NEWSLETTER_CONFIRM_KEY), any(Map.class));
    }

    @Test
    void subscribe_WithUserId_ShouldBeActiveAndSendWelcome() {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("user@example.com");
        request.setUserId(123L);

        when(subscriberRepository.findByEmailAndFollowedAuthorId("user@example.com", null)).thenReturn(Optional.empty());
        when(subscriberRepository.save(any(Subscriber.class))).thenAnswer(invocation -> invocation.getArgument(0));

        newsletterService.subscribe(request);

        verify(subscriberRepository).save(argThat(s -> s.getStatus() == SubscriberStatus.ACTIVE));
        verify(rabbitTemplate).convertAndSend(eq(RabbitMQConfig.NEWSLETTER_EXCHANGE), eq(RabbitMQConfig.NEWSLETTER_CONFIRM_KEY), any(Map.class));
    }

    @Test
    void confirmSubscription_ShouldActivate() {
        Subscriber subscriber = Subscriber.builder()
                .email("test@example.com")
                .token("token")
                .status(SubscriberStatus.PENDING)
                .tokenExpiresAt(LocalDateTime.now().plusHours(1))
                .build();
        when(subscriberRepository.findByToken("token")).thenReturn(Optional.of(subscriber));

        newsletterService.confirmSubscription("token");

        assertEquals(SubscriberStatus.ACTIVE, subscriber.getStatus());
        verify(subscriberRepository).save(subscriber);
    }

    @Test
    void sendCampaign_ShouldFilterAndSendToQueue() {
        CampaignRequest request = new CampaignRequest();
        request.setSubject("Subject");
        request.setContent("Content");
        request.setTags(List.of("java"));

        Subscriber s1 = Subscriber.builder().email("s1@test.com").status(SubscriberStatus.ACTIVE).preferences("java").build();
        Subscriber s2 = Subscriber.builder().email("s2@test.com").status(SubscriberStatus.ACTIVE).preferences("python").build();
        
        when(subscriberRepository.findByStatus(SubscriberStatus.ACTIVE)).thenReturn(List.of(s1, s2));

        newsletterService.sendCampaign(request, null);

        verify(rabbitTemplate, times(1)).convertAndSend(eq(RabbitMQConfig.NEWSLETTER_EXCHANGE), eq(RabbitMQConfig.NEWSLETTER_SEND_KEY), any(Map.class));
    }

    @Test
    void getAnalytics_ShouldReturnCorrectStats() {
        when(subscriberRepository.count()).thenReturn(10L);
        when(subscriberRepository.countByStatus(SubscriberStatus.ACTIVE)).thenReturn(7L);
        when(subscriberRepository.countByStatus(SubscriberStatus.PENDING)).thenReturn(2L);
        when(subscriberRepository.countByStatus(SubscriberStatus.UNSUBSCRIBED)).thenReturn(1L);

        NewsletterAnalytics analytics = newsletterService.getAnalytics(null);

        assertEquals(10L, analytics.getTotalSubscribers());
        assertEquals(7L, analytics.getActiveSubscribers());
        assertEquals(2L, analytics.getPendingSubscribers());
        assertEquals(1L, analytics.getUnsubscribedCount());
    }

    @Test
    void unsubscribe_ShouldUpdateStatus() {
        Subscriber subscriber = Subscriber.builder().status(SubscriberStatus.ACTIVE).build();
        when(subscriberRepository.findByToken("token")).thenReturn(Optional.of(subscriber));

        newsletterService.unsubscribe("token");

        assertEquals(SubscriberStatus.UNSUBSCRIBED, subscriber.getStatus());
        verify(subscriberRepository).save(subscriber);
    }

    @Test
    void subscribe_ExistingPending_ShouldUpdateInfoAndResend() {
        SubscribeRequest request = new SubscribeRequest();
        request.setEmail("test@test.com");
        request.setUserId(5L);
        request.setFullName("New Name");

        Subscriber existing = Subscriber.builder().email("test@test.com").status(SubscriberStatus.PENDING).build();
        when(subscriberRepository.findByEmailAndFollowedAuthorId("test@test.com", null)).thenReturn(Optional.of(existing));

        newsletterService.subscribe(request);

        assertEquals(5L, existing.getUserId());
        assertEquals("New Name", existing.getFullName());
        verify(subscriberRepository).save(existing);
    }

    @Test
    void confirmSubscription_Expired_ShouldThrow() {
        Subscriber s = Subscriber.builder().tokenExpiresAt(LocalDateTime.now().minusDays(1)).build();
        when(subscriberRepository.findByToken("token")).thenReturn(Optional.of(s));

        CustomException ex = assertThrows(CustomException.class, () -> newsletterService.confirmSubscription("token"));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
    }

    @Test
    void unsubscribeByEmail_ShouldWork() {
        Subscriber s = Subscriber.builder().email("test@test.com").build();
        when(subscriberRepository.findByEmailAndFollowedAuthorId("test@test.com", 1L)).thenReturn(Optional.of(s));

        newsletterService.unsubscribeByEmail("test@test.com", 1L);

        assertEquals(SubscriberStatus.UNSUBSCRIBED, s.getStatus());
        verify(subscriberRepository).save(s);
    }

    @Test
    void resendConfirmation_ShouldWork() {
        Subscriber s = Subscriber.builder().email("test@test.com").status(SubscriberStatus.PENDING).build();
        when(subscriberRepository.findByEmail("test@test.com")).thenReturn(Optional.of(s));

        ResendConfirmationRequest req = new ResendConfirmationRequest();
        req.setEmail("test@test.com");
        newsletterService.resendConfirmation(req);

        verify(subscriberRepository).save(s);
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Map.class));
    }

    @Test
    void sendPostNotification_ShouldWork() {
        Subscriber s = Subscriber.builder().email("s1@test.com").status(SubscriberStatus.ACTIVE).build();
        when(subscriberRepository.findByStatus(SubscriberStatus.ACTIVE)).thenReturn(List.of(s));

        newsletterService.sendPostNotification("Title", "url", null);

        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), any(Map.class));
    }

    @Test
    void updatePreferences_ShouldWork() {
        Subscriber s = new Subscriber();
        when(subscriberRepository.findById(1L)).thenReturn(Optional.of(s));
        
        PreferenceRequest req = new PreferenceRequest();
        req.setPreferences("new-prefs");
        newsletterService.updatePreferences(1L, req);

        assertEquals("new-prefs", s.getPreferences());
        verify(subscriberRepository).save(s);
    }
}
