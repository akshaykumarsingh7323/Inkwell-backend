package com.inkwell.payment.service;

import com.inkwell.payment.entity.Subscription;

public interface SubscriptionService {
    Subscription createSubscription(String userId, String plan);
    boolean hasActiveSubscription(String userId);
    Subscription getSubscription(String userId);
}
