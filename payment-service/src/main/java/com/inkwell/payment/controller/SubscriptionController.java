package com.inkwell.payment.controller;

import com.inkwell.payment.entity.Subscription;
import com.inkwell.payment.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {

    @Autowired
    private SubscriptionService subscriptionService;

    @PostMapping("/create")
    public ResponseEntity<Subscription> createSubscription(@RequestParam String userId, @RequestParam(required = false) String plan) {
        return ResponseEntity.ok(subscriptionService.createSubscription(userId, plan));
    }

    @GetMapping("/status")
    public ResponseEntity<Boolean> getStatus(@RequestParam String userId) {
        return ResponseEntity.ok(subscriptionService.hasActiveSubscription(userId));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<Subscription> getSubscription(@PathVariable String userId) {
        return ResponseEntity.ok(subscriptionService.getSubscription(userId));
    }
}
