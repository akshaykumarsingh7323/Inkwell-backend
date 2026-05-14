package com.inkwell.payment.controller;

import com.inkwell.payment.entity.Subscription;
import com.inkwell.payment.service.SubscriptionService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SubscriptionControllerTest {

    @Mock
    private SubscriptionService subscriptionService;

    @InjectMocks
    private SubscriptionController subscriptionController;

    @Test
    void createSubscription_ShouldReturnSubscription() {
        Subscription subscription = new Subscription();
        when(subscriptionService.createSubscription("1", "BASIC")).thenReturn(subscription);

        assertEquals(subscription, subscriptionController.createSubscription("1", "BASIC").getBody());
    }

    @Test
    void getStatus_ShouldReturnFlag() {
        when(subscriptionService.hasActiveSubscription("1")).thenReturn(true);

        assertEquals(true, subscriptionController.getStatus("1").getBody());
    }

    @Test
    void getSubscription_ShouldReturnSubscription() {
        Subscription subscription = new Subscription();
        when(subscriptionService.getSubscription("1")).thenReturn(subscription);

        assertEquals(subscription, subscriptionController.getSubscription("1").getBody());
    }
}
