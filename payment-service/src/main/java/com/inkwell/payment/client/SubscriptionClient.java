package com.inkwell.payment.client;

import lombok.Data;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDateTime;

@FeignClient(name = "subscription-service", url = "${SUBSCRIPTION_SERVICE_URL:http://localhost:8088}")
public interface SubscriptionClient {

    @PostMapping("/subscriptions/create")
    SubscriptionResponse createSubscription(@RequestParam("userId") String userId, @RequestParam(value = "plan", required = false) String plan);

    @GetMapping("/subscriptions/status")
    boolean hasActiveSubscription(@RequestParam("userId") String userId);

    @GetMapping("/subscriptions/{userId}")
    SubscriptionResponse getSubscription(@PathVariable("userId") String userId);

    @Data
    class SubscriptionResponse {
        private String subscriptionId;
        private String userId;
        private String plan;
        private LocalDateTime startDate;
        private LocalDateTime expiryDate;
        private String status;
    }
}
