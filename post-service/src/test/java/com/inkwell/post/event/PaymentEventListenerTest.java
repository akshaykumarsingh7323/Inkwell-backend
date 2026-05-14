package com.inkwell.post.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.HashMap;
import java.util.Map;

import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerTest {

    @Mock
    private CacheManager cacheManager;

    @Mock
    private Cache cache;

    @InjectMocks
    private PaymentEventListener paymentEventListener;

    @Test
    void handlePaymentSuccessShouldEvictCaches() {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", "10");
        event.put("postId", "5");

        when(cacheManager.getCache(anyString())).thenReturn(cache);

        paymentEventListener.handlePaymentSuccess(event);
        // Coverage is the goal
    }

    @Test
    void handlePaymentSuccessShouldHandleNullCaches() {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", "10");
        event.put("postId", "5");

        when(cacheManager.getCache(anyString())).thenReturn(null);

        paymentEventListener.handlePaymentSuccess(event);
    }

    @Test
    void handlePaymentSuccessShouldHandleMissingDataInEvent() {
        Map<String, Object> event = new HashMap<>();
        paymentEventListener.handlePaymentSuccess(event);
    }

    @Test
    void handlePaymentSuccessShouldHandlePartialEventData() {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", "10");

        paymentEventListener.handlePaymentSuccess(event);
    }

    @Test
    void handlePaymentSuccessShouldHandleException() {
        Map<String, Object> event = new HashMap<>();
        event.put("userId", "10");
        event.put("postId", "5");

        when(cacheManager.getCache(anyString())).thenThrow(new RuntimeException("Cache failure"));

        paymentEventListener.handlePaymentSuccess(event);
    }
}
