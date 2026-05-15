package com.inkwell.newsletter.service.impl;

import com.inkwell.newsletter.config.RabbitMQConfig;
import com.inkwell.newsletter.dto.*;
import com.inkwell.newsletter.entity.Subscriber;
import com.inkwell.newsletter.entity.SubscriberStatus;
import com.inkwell.newsletter.exception.CustomException;
import com.inkwell.newsletter.repository.SubscriberRepository;
import com.inkwell.newsletter.service.NewsletterService;
import com.inkwell.newsletter.util.TokenUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import jakarta.persistence.criteria.Predicate;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NewsletterServiceImpl implements NewsletterService {

    private final SubscriberRepository subscriberRepository;
    private final RabbitTemplate rabbitTemplate;

    private static final int TOKEN_EXPIRY_HOURS = 48;

    @Value("${app.frontend-url:http://localhost:4200}")
    private String frontendUrl;

    @Override
    @Transactional
    public void subscribe(SubscribeRequest request) {
        log.info("Subscription request: email={}, userId={}, authorId={}", 
            request.getEmail(), request.getUserId(), request.getFollowedAuthorId());
            
        Long authorId = (request.getFollowedAuthorId() == null || request.getFollowedAuthorId() == 0) ? null : request.getFollowedAuthorId();
        Optional<Subscriber> existingOpt = subscriberRepository.findByEmailAndFollowedAuthorId(request.getEmail(), authorId);
        
        Subscriber subscriber;
        // Automatically activate if the user is already logged in (has userId)
        SubscriberStatus targetStatus = (request.getUserId() != null) ? SubscriberStatus.ACTIVE : SubscriberStatus.PENDING;

        if (existingOpt.isPresent()) {
            subscriber = existingOpt.get();
            if (subscriber.getStatus() == SubscriberStatus.ACTIVE) {
                log.info("Subscriber {} is already active for author {}", request.getEmail(), authorId);
                return;
            }
            // Update info if it was missing (e.g. guest became logged in user)
            if (subscriber.getUserId() == null && request.getUserId() != null) {
                subscriber.setUserId(request.getUserId());
            }
            if (subscriber.getFullName() == null && request.getFullName() != null) {
                subscriber.setFullName(request.getFullName());
            }
            subscriber.setStatus(targetStatus);
            // Reuse existing token if present to keep previous email links valid
            if (subscriber.getToken() == null) {
                subscriber.setToken(TokenUtil.generateToken());
            }
            subscriber.setTokenExpiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
        } else {
            subscriber = Subscriber.builder()
                    .email(request.getEmail())
                    .fullName(request.getFullName())
                    .userId(request.getUserId())
                    .followedAuthorId(authorId)
                    .status(targetStatus)
                    .token(TokenUtil.generateToken())
                    .tokenExpiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS))
                    .build();
        }

        subscriberRepository.save(subscriber);
        
        try {
            if (subscriber.getStatus() == SubscriberStatus.ACTIVE) {
                sendWelcomeEmail(subscriber.getEmail(), subscriber.getToken());
            } else {
                triggerConfirmationEmail(subscriber);
            }
        } catch (Exception e) {
            log.error("Failed to send notification email to {}. RabbitMQ might be down.", subscriber.getEmail(), e);
            // We still saved the subscriber, but we should inform the user about the email delay
            throw new CustomException("Subscription saved, but we're having trouble sending the notification email. Please try again later.", HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    @Override
    @Transactional
    public void confirmSubscription(String token) {
        Subscriber subscriber = subscriberRepository.findByToken(token)
                .orElseThrow(() -> new CustomException("Invalid confirmation token", HttpStatus.BAD_REQUEST));

        if (subscriber.getStatus() == SubscriberStatus.ACTIVE) {
            return;
        }

        if (subscriber.getTokenExpiresAt() != null && subscriber.getTokenExpiresAt().isBefore(LocalDateTime.now())) {
            throw new CustomException("Token expired", HttpStatus.BAD_REQUEST);
        }

        subscriber.setStatus(SubscriberStatus.ACTIVE);
        subscriberRepository.save(subscriber);
        sendWelcomeEmail(subscriber.getEmail(), subscriber.getToken());
    }

    @Override
    @Transactional
    public void unsubscribe(String token) {
        Subscriber subscriber = subscriberRepository.findByToken(token)
                .orElseThrow(() -> new CustomException("Invalid token", HttpStatus.BAD_REQUEST));

        subscriber.setStatus(SubscriberStatus.UNSUBSCRIBED);
        subscriber.setUnsubscribedAt(LocalDateTime.now());
        subscriberRepository.save(subscriber);
    }

    @Override
    @Transactional
    public void unsubscribeByEmail(String email, Long followedAuthorId) {
        Subscriber subscriber = (followedAuthorId == null || followedAuthorId == 0)
                ? subscriberRepository.findByEmail(email).orElseThrow(() -> new CustomException("Not found", HttpStatus.NOT_FOUND))
                : subscriberRepository.findByEmailAndFollowedAuthorId(email, followedAuthorId)
                .orElseThrow(() -> new CustomException("Not found", HttpStatus.NOT_FOUND));

        subscriber.setStatus(SubscriberStatus.UNSUBSCRIBED);
        subscriber.setUnsubscribedAt(LocalDateTime.now());
        subscriberRepository.save(subscriber);
    }

    @Override
    @Transactional
    public void resendConfirmation(ResendConfirmationRequest request) {
        Subscriber subscriber = subscriberRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new CustomException("Email not found", HttpStatus.NOT_FOUND));

        if (subscriber.getStatus() == SubscriberStatus.ACTIVE) {
            throw new CustomException("Already active", HttpStatus.BAD_REQUEST);
        }

        subscriber.setToken(TokenUtil.generateToken());
        subscriber.setTokenExpiresAt(LocalDateTime.now().plusHours(TOKEN_EXPIRY_HOURS));
        subscriberRepository.save(subscriber);
        triggerConfirmationEmail(subscriber);
    }

    @Override
    public Subscriber getSubscriberByEmail(String email, Long followedAuthorId) {
        if (followedAuthorId == null || followedAuthorId == 0) {
            return subscriberRepository.findByEmailAndFollowedAuthorId(email, null)
                    .orElseThrow(() -> new CustomException("Subscriber not found", HttpStatus.NOT_FOUND));
        }
        return subscriberRepository.findByEmailAndFollowedAuthorId(email, followedAuthorId)
                .orElseThrow(() -> new CustomException("Subscriber not found", HttpStatus.NOT_FOUND));
    }

    @Override
    public List<Subscriber> getAllSubscribers(Long followedAuthorId) {
        if (followedAuthorId == null || followedAuthorId == 0) {
            return subscriberRepository.findAll();
        }
        return subscriberRepository.findByFollowedAuthorIdAndStatus(followedAuthorId, SubscriberStatus.ACTIVE);
    }

    @Override
    public void sendNewsletter(NewsletterRequest request) {
        List<Subscriber> recipients = subscriberRepository.findByStatus(SubscriberStatus.ACTIVE);
        for (Subscriber s : recipients) {
            Map<String, Object> data = new HashMap<>();
            data.put("email", s.getEmail());
            data.put("subject", request.getSubject());
            data.put("content", request.getContent());
            data.put("template", "campaign");
            data.put("unsubscribeUrl", frontendUrl + "/newsletter/unsubscribe?token=" + s.getToken());
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.NEWSLETTER_EXCHANGE, RabbitMQConfig.NEWSLETTER_SEND_KEY, data);
        }
    }

    @Override
    public void sendCampaign(CampaignRequest request, Long authorId) {
        List<Subscriber> recipients = subscriberRepository.findByStatus(SubscriberStatus.ACTIVE);
        if (authorId != null && authorId != 0) {
            recipients = recipients.stream().filter(s -> authorId.equals(s.getFollowedAuthorId())).collect(Collectors.toList());
        }

        if (request.getTags() != null && !request.getTags().isEmpty()) {
            recipients = recipients.stream()
                    .filter(s -> s.getPreferences() != null && request.getTags().stream().anyMatch(tag -> s.getPreferences().toLowerCase().contains(tag.toLowerCase())))
                    .collect(Collectors.toList());
        }

        for (Subscriber s : recipients) {
            Map<String, Object> data = new HashMap<>();
            data.put("email", s.getEmail());
            data.put("subject", request.getSubject());
            data.put("content", request.getContent());
            data.put("template", "campaign");
            data.put("unsubscribeUrl", frontendUrl + "/newsletter/unsubscribe?token=" + s.getToken());
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.NEWSLETTER_EXCHANGE, RabbitMQConfig.NEWSLETTER_SEND_KEY, data);
        }
    }

    @Override
    public void sendPostNotification(String postTitle, String postUrl, Long authorId) {
        List<Subscriber> recipients = (authorId != null && authorId != 0)
                ? subscriberRepository.findByFollowedAuthorIdAndStatus(authorId, SubscriberStatus.ACTIVE)
                : subscriberRepository.findByStatus(SubscriberStatus.ACTIVE);

        for (Subscriber s : recipients) {
            Map<String, Object> data = new HashMap<>();
            data.put("email", s.getEmail());
            data.put("postTitle", postTitle);
            data.put("postUrl", postUrl);
            data.put("template", "post-notification");
            data.put("subject", "New Post: " + postTitle);
            data.put("unsubscribeUrl", frontendUrl + "/newsletter/unsubscribe?token=" + s.getToken());
            
            rabbitTemplate.convertAndSend(RabbitMQConfig.NEWSLETTER_EXCHANGE, RabbitMQConfig.NEWSLETTER_SEND_KEY, data);
        }
    }

    @Override
    public void updatePreferences(Long subscriberId, PreferenceRequest request) {
        Subscriber subscriber = subscriberRepository.findById(subscriberId)
                .orElseThrow(() -> new CustomException("Not found", HttpStatus.NOT_FOUND));
        subscriber.setPreferences(request.getPreferences());
        subscriberRepository.save(subscriber);
    }

    @Override
    public long getSubscriberCount(Long followedAuthorId) {
        if (followedAuthorId == null || followedAuthorId == 0) return subscriberRepository.countByStatus(SubscriberStatus.ACTIVE);
        return subscriberRepository.countByFollowedAuthorIdAndStatus(followedAuthorId, SubscriberStatus.ACTIVE);
    }

    @Override
    public void sendWelcomeEmail(String email, String unsubscribeToken) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", email);
        data.put("template", "welcome");
        data.put("subject", "Welcome to InkWell!");
        data.put("unsubscribeUrl", frontendUrl + "/newsletter/unsubscribe?token=" + unsubscribeToken);
        
        rabbitTemplate.convertAndSend(RabbitMQConfig.NEWSLETTER_EXCHANGE, RabbitMQConfig.NEWSLETTER_CONFIRM_KEY, data);
    }

    @Override
    public NewsletterAnalytics getAnalytics(Long authorId) {
        boolean isGeneral = (authorId == null || authorId == 0L);
        long total = isGeneral ? subscriberRepository.count() : subscriberRepository.countByFollowedAuthorId(authorId);
        
        return NewsletterAnalytics.builder()
                .totalSubscribers(total)
                .activeSubscribers(isGeneral ? subscriberRepository.countByStatus(SubscriberStatus.ACTIVE) : subscriberRepository.countByFollowedAuthorIdAndStatus(authorId, SubscriberStatus.ACTIVE))
                .pendingSubscribers(isGeneral ? subscriberRepository.countByStatus(SubscriberStatus.PENDING) : subscriberRepository.countByFollowedAuthorIdAndStatus(authorId, SubscriberStatus.PENDING))
                .unsubscribedCount(isGeneral ? subscriberRepository.countByStatus(SubscriberStatus.UNSUBSCRIBED) : subscriberRepository.countByFollowedAuthorIdAndStatus(authorId, SubscriberStatus.UNSUBSCRIBED))
                .preferenceDistribution(new HashMap<>())
                .build();
    }

    @Override
    public Page<Subscriber> searchSubscribers(String query, SubscriberStatus status, String preference, Pageable pageable) {
        Specification<Subscriber> spec = (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (query != null && !query.isEmpty()) {
                String search = "%" + query.toLowerCase() + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("email")), search),
                    cb.like(cb.lower(root.get("fullName")), search)
                ));
            }

            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }

            if (preference != null && !preference.isEmpty()) {
                predicates.add(cb.like(cb.lower(root.get("preferences")), "%" + preference.toLowerCase() + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        return subscriberRepository.findAll(spec, pageable);
    }

    @Override
    public Subscriber getSubscriberById(Long id) {
        return subscriberRepository.findById(id)
                .orElseThrow(() -> new CustomException("Subscriber not found", HttpStatus.NOT_FOUND));
    }

    private void triggerConfirmationEmail(Subscriber subscriber) {
        Map<String, Object> data = new HashMap<>();
        data.put("email", subscriber.getEmail());
        data.put("name", subscriber.getFullName());
        data.put("template", "confirmation");
        data.put("subject", "Confirm your subscription to InkWell");
        data.put("confirmUrl", frontendUrl + "/newsletter/confirm?token=" + subscriber.getToken());
        
        rabbitTemplate.convertAndSend(RabbitMQConfig.NEWSLETTER_EXCHANGE, RabbitMQConfig.NEWSLETTER_CONFIRM_KEY, data);
    }
}
