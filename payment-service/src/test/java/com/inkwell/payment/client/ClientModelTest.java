package com.inkwell.payment.client;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ClientModelTest {

    @Test
    void postSummary_GettersAndSetters_ShouldWork() {
        PostClient.PostSummary summary = new PostClient.PostSummary();
        summary.setPostId(1L);
        summary.setAuthorId(2L);
        summary.setTitle("Title");

        assertEquals(1L, summary.getPostId());
        assertEquals(2L, summary.getAuthorId());
        assertEquals("Title", summary.getTitle());
    }

    @Test
    void subscriptionResponse_GettersAndSetters_ShouldWork() {
        SubscriptionClient.SubscriptionResponse response = new SubscriptionClient.SubscriptionResponse();
        LocalDateTime now = LocalDateTime.now();
        response.setSubscriptionId("sub-1");
        response.setUserId("1");
        response.setPlan("BASIC");
        response.setStartDate(now);
        response.setExpiryDate(now.plusDays(30));
        response.setStatus("ACTIVE");

        assertEquals("sub-1", response.getSubscriptionId());
        assertEquals("1", response.getUserId());
        assertEquals("BASIC", response.getPlan());
        assertEquals(now, response.getStartDate());
        assertEquals(now.plusDays(30), response.getExpiryDate());
        assertEquals("ACTIVE", response.getStatus());
    }
}
