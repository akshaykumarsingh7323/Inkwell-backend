package com.inkwell.payment.service.impl;

import com.inkwell.payment.entity.Subscription;
import com.inkwell.payment.exception.SubscriptionException;
import com.inkwell.payment.repository.SubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionServiceImplTest {

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @InjectMocks
    private SubscriptionServiceImpl subscriptionService;

    @Test
    void createSubscription_Success() {
        when(subscriptionRepository.findByUserIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Subscription result = subscriptionService.createSubscription("user1", "ANNUAL");

        assertNotNull(result);
        assertEquals("user1", result.getUserId());
        assertEquals("ANNUAL", result.getPlan());
        assertEquals(Subscription.SubscriptionStatus.ACTIVE, result.getStatus());
    }

    @Test
    void createSubscription_WithNullPlan_ShouldDefaultToMonthly() {
        when(subscriptionRepository.findByUserIdAndStatus(any(), any())).thenReturn(Optional.empty());
        when(subscriptionRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Subscription result = subscriptionService.createSubscription("user1", null);

        assertEquals("MONTHLY", result.getPlan());
    }

    @Test
    void createSubscription_DuplicateActive_ShouldThrowException() {
        Subscription active = Subscription.builder()
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .build();
        when(subscriptionRepository.findByUserIdAndStatus("user1", Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        assertThrows(SubscriptionException.class, () -> subscriptionService.createSubscription("user1", "MONTHLY"));
    }

    @Test
    void hasActiveSubscription_WhenExpired_ShouldReturnFalse() {
        Subscription expired = Subscription.builder()
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .expiryDate(LocalDateTime.now().minusDays(1))
                .build();
        when(subscriptionRepository.findByUserIdAndStatus("user1", Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(expired));

        assertFalse(subscriptionService.hasActiveSubscription("user1"));
    }

    @Test
    void hasActiveSubscription_WhenPresentAndValid_ShouldReturnTrue() {
        Subscription active = Subscription.builder()
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .expiryDate(LocalDateTime.now().plusDays(1))
                .build();
        when(subscriptionRepository.findByUserIdAndStatus("user1", Subscription.SubscriptionStatus.ACTIVE))
                .thenReturn(Optional.of(active));

        assertTrue(subscriptionService.hasActiveSubscription("user1"));
    }

    @Test
    void getSubscription_WhenMissing_ShouldThrow() {
        when(subscriptionRepository.findByUserId("user1")).thenReturn(Optional.empty());
        assertThrows(SubscriptionException.class, () -> subscriptionService.getSubscription("user1"));
    }

    @Test
    void getSubscription_ShouldReturnSubscription() {
        Subscription subscription = Subscription.builder().userId("user1").plan("MONTHLY").build();
        when(subscriptionRepository.findByUserId("user1")).thenReturn(Optional.of(subscription));
        assertEquals("MONTHLY", subscriptionService.getSubscription("user1").getPlan());
    }
}
