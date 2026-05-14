package com.inkwell.payment.service.impl;

import com.inkwell.payment.entity.Subscription;
import com.inkwell.payment.repository.SubscriptionRepository;
import com.inkwell.payment.service.SubscriptionService;
import com.inkwell.payment.exception.SubscriptionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class SubscriptionServiceImpl implements SubscriptionService {

    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @Override
    public Subscription createSubscription(String userId, String plan) {
        // Prevent duplicate active subscriptions
        if (hasActiveSubscription(userId)) {
            throw new SubscriptionException("User already has an active subscription");
        }

        Subscription subscription = Subscription.builder()
                .userId(userId)
                .plan(plan != null ? plan : "MONTHLY")
                .startDate(LocalDateTime.now())
                .expiryDate(LocalDateTime.now().plusDays(30))
                .status(Subscription.SubscriptionStatus.ACTIVE)
                .build();

        return subscriptionRepository.save(subscription);
    }

    @Override
    public boolean hasActiveSubscription(String userId) {
        return subscriptionRepository.findByUserIdAndStatus(userId, Subscription.SubscriptionStatus.ACTIVE)
                .map(sub -> sub.getExpiryDate().isAfter(LocalDateTime.now()))
                .orElse(false);
    }

    @Override
    public Subscription getSubscription(String userId) {
        return subscriptionRepository.findByUserId(userId)
                .orElseThrow(() -> new SubscriptionException("No subscription found for user: " + userId));
    }
}
