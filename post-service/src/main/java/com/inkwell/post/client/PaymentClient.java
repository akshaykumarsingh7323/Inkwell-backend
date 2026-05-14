package com.inkwell.post.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "payment-service", url = "${PAYMENT_SERVICE_URL:http://localhost:8089}")
public interface PaymentClient {

    @GetMapping("/payments/check")
    boolean hasAccess(@RequestParam("userId") String userId, @RequestParam("postId") String postId);
}
