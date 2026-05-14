package com.inkwell.newsletter.service;

import com.inkwell.newsletter.dto.*;
import com.inkwell.newsletter.entity.Subscriber;
import com.inkwell.newsletter.entity.SubscriberStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;

public interface NewsletterService {
    void subscribe(SubscribeRequest request);
    void unsubscribe(String token);
    void unsubscribeByEmail(String email, Long followedAuthorId);
    void confirmSubscription(String token);
    void resendConfirmation(ResendConfirmationRequest request);
    Subscriber getSubscriberByEmail(String email, Long followedAuthorId);
    List<Subscriber> getAllSubscribers(Long followedAuthorId);
    void sendNewsletter(NewsletterRequest request);
    void sendCampaign(CampaignRequest request, Long authorId);
    void sendPostNotification(String postTitle, String postUrl, Long authorId);
    void updatePreferences(Long subscriberId, PreferenceRequest request);
    long getSubscriberCount(Long followedAuthorId);
    void sendWelcomeEmail(String email, String unsubscribeToken);
    NewsletterAnalytics getAnalytics(Long authorId);
    Page<Subscriber> searchSubscribers(String query, SubscriberStatus status, String preference, Pageable pageable);
    Subscriber getSubscriberById(Long id);
}

