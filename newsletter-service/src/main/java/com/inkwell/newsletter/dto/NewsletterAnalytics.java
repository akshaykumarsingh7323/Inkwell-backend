package com.inkwell.newsletter.dto;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class NewsletterAnalytics {
    private long totalSubscribers;
    private long activeSubscribers;
    private long pendingSubscribers;
    private long unsubscribedCount;
    private Map<String, Long> preferenceDistribution;
}
