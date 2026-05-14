package com.inkwell.post.event;

import com.inkwell.post.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final CacheManager cacheManager;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_SUCCESS_QUEUE)
    public void handlePaymentSuccess(Map<String, Object> event) {
        log.info("Received payment success event: {}", event);
        
        try {
            String userId = String.valueOf(event.get("userId"));
            String postId = String.valueOf(event.get("postId"));
            
            if (userId != null && postId != null) {
                log.info("Evicting post cache for userId: {} and postId: {}", userId, postId);
                
                // Evict individual post cache entries that might be affected
                // Note: The key in PostServiceImpl is #slug + '_' + (#requesterId ?: 'anonymous')
                // or #postId + '_' + (#requesterId ?: 'anonymous')
                
                // Since we don't have the slug here easily, and cache invalidation by prefix is hard with default Redis/Ehcache
                // We will clear the "post" and "posts" cache to be safe, or we can try to be more specific if we had the slug.
                
                log.info("Evicting all post caches due to payment success for postId: {}", postId);
                
                // Clear all post-related caches to ensure consistency across all pages
                String[] cachesToClear = {"post", "posts", "search_posts", "trending_posts"};
                for (String cacheName : cachesToClear) {
                    if (cacheManager.getCache(cacheName) != null) {
                        cacheManager.getCache(cacheName).clear();
                        log.info("Cleared '{}' cache.", cacheName);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to process payment success event", e);
        }
    }
}
