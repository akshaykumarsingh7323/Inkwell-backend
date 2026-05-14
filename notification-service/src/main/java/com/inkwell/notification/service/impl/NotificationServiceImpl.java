package com.inkwell.notification.service.impl;

import com.inkwell.notification.dto.NotificationRequest;
import com.inkwell.notification.dto.NotificationResponse;
import com.inkwell.notification.entity.Notification;
import com.inkwell.notification.exception.CustomException;
import com.inkwell.notification.repository.NotificationRepository;
import com.inkwell.notification.service.NotificationService;
import com.inkwell.notification.service.AuditService;
import org.springframework.scheduling.annotation.Async;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.web.client.RestTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final JavaMailSender mailSender;
    private final AuditService auditService;
    private final RestTemplate restTemplate;

    @Value("${auth.service.url:http://auth-service}")
    private String authServiceUrl;

    @Override
    @Transactional
    public void notifyAdmins(String title, String message, Long relatedId, String relatedSlug) {
        log.info("Notifying all admins about: {}", title);
        try {
            List<Long> adminIds = fetchUserIdsByRole("ADMIN");
            if (adminIds.isEmpty()) {
                log.warn("No admins found to notify.");
                return;
            }

            NotificationRequest request = NotificationRequest.builder()
                    .actorId(0L) // System actor
                    .type(com.inkwell.notification.enums.NotificationType.ADMIN_BROADCAST)
                    .title(title)
                    .message(message)
                    .relatedId(relatedId)
                    .relatedType("POST")
                    .relatedSlug(relatedSlug)
                    .build();

            sendBulk(adminIds, request);
            log.info("Successfully notified {} admins.", adminIds.size());
        } catch (Exception e) {
            log.error("Failed to notify admins", e);
        }
    }

    private List<Long> fetchUserIdsByRole(String role) {
        String url = authServiceUrl + "/auth/users/by-role?role=" + role;
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-User-Role", "ADMIN"); // Self-auth for internal call

        log.debug("Fetching user IDs for role: {} from auth-service at URL: {}", role, url);
        try {
            ResponseEntity<List<Long>> response = restTemplate.exchange(
                    url,
                    HttpMethod.GET,
                    new HttpEntity<>(headers),
                    new ParameterizedTypeReference<List<Long>>() {});

            List<Long> ids = response.getBody() != null ? response.getBody() : List.of();
            log.info("Auth-service responded with {} IDs for role {}: {}", ids.size(), role, ids);
            return ids;
        } catch (Exception e) {
            log.error("Failed to fetch user IDs for role: {} from auth-service. Error: {}", role, e.getMessage());
            log.debug("Full exception stack trace:", e);
            return List.of();
        }
    }
    
    private String fetchUserEmail(Long userId) {
        String url = authServiceUrl + "/auth/users/" + userId + "/email";
        log.debug("Fetching email for userId: {} from auth-service at URL: {}", userId, url);
        try {
            String email = restTemplate.getForObject(url, String.class);
            log.debug("Successfully fetched email for userId: {}: {}", userId, email);
            return email;
        } catch (Exception e) {
            log.error("Failed to fetch email for userId: {} from auth-service. Error: {}", userId, e.getMessage());
            return null;
        }
    }

    @Override
    @Transactional
    public NotificationResponse send(NotificationRequest request) {
        Notification notification = Notification.builder()
                .recipientId(request.getRecipientId())
                .actorId(request.getActorId())
                .type(request.getType())
                .title(request.getTitle())
                .message(request.getMessage())
                .relatedId(request.getRelatedId())
                .relatedType(request.getRelatedType())
                .relatedSlug(request.getRelatedSlug())
                .build();

        Notification saved = notificationRepository.save(notification);

        if (request.isSendEmail() && request.getRecipientEmail() != null) {
            sendEmail(request.getRecipientEmail(), request.getTitle(), request.getMessage());
        }

        // Audit the notification
        auditService.log(
                request.getActorId(),
                "SEND_NOTIFICATION",
                "NOTIFICATION",
                saved.getNotificationId(),
                "Sent " + request.getType() + " notification to " + request.getRecipientId()
        );

        return mapToResponse(saved);
    }

    @Override
    @Transactional
    public void sendBulk(List<Long> recipientIds, NotificationRequest request) {
        List<Notification> notifications = recipientIds.stream()
                .map(recipientId -> Notification.builder()
                        .recipientId(recipientId)
                        .actorId(request.getActorId())
                        .type(request.getType())
                        .title(request.getTitle())
                        .message(request.getMessage())
                        .relatedId(request.getRelatedId())
                        .relatedType(request.getRelatedType())
                        .relatedSlug(request.getRelatedSlug())
                        .build())
                .collect(Collectors.toList());

        notificationRepository.saveAll(notifications);
        
        // In a real app, email bulk sending would be handled asynchronously
        log.info("Bulk notifications sent to {} recipients", recipientIds.size());
    }

    @Override
    @Transactional
    public void sendBulkNotification(List<Long> recipientIds, String title, String message, String type) {
        NotificationRequest request = NotificationRequest.builder()
                .actorId(0L) // System actor
                .type(com.inkwell.notification.enums.NotificationType.valueOf(type))
                .title(title)
                .message(message)
                .build();
        sendBulk(recipientIds, request);
        log.info("Broadcast '{}' sent to {} users", title, recipientIds.size());
    }

    @Override
    @Transactional
    public void sendBroadcastToAll(String title, String message) {
        // Fetch all distinct recipient IDs that have previously received notifications
        // In a real production system, this would call auth-service for all user IDs.
        // For now we notify all IDs known from existing notification records.
        List<Long> allUserIds = notificationRepository.findAll().stream()
                .map(Notification::getRecipientId)
                .distinct()
                .collect(Collectors.toList());
        if (!allUserIds.isEmpty()) {
            sendBulkNotification(allUserIds, title, message, "ADMIN_BROADCAST");
        }
        log.info("Broadcast to all: {} known users", allUserIds.size());
    }

    @Override
    @Transactional
    public void markAsRead(Long notificationId) {
        notificationRepository.markAsRead(notificationId);
    }

    @Override
    @Transactional
    public void markAllRead(Long recipientId) {
        notificationRepository.markAllRead(recipientId);
    }

    @Override
    @Transactional
    public void deleteRead(Long recipientId) {
        notificationRepository.deleteByRecipientIdAndIsRead(recipientId, true);
    }

    @Override
    public List<NotificationResponse> getByRecipient(Long recipientId) {
        return notificationRepository.findByRecipientIdOrderByCreatedAtDesc(recipientId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public long getUnreadCount(Long recipientId) {
        return notificationRepository.countByRecipientIdAndIsRead(recipientId, false);
    }

    @Override
    @Transactional
    public void deleteNotification(Long notificationId) {
        notificationRepository.deleteById(notificationId);
    }

    @Override
    public NotificationResponse getNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new CustomException("Notification not found", HttpStatus.NOT_FOUND));
        return mapToResponse(notification);
    }

    @Override
    @Async
    public void sendEmail(String email, String subject, String message) {
        try {
            SimpleMailMessage mailMessage = new SimpleMailMessage();
            mailMessage.setFrom("no-reply@inkwell.com");
            mailMessage.setTo(email);
            mailMessage.setSubject(subject);
            mailMessage.setText(message);
            mailSender.send(mailMessage);
            log.info("Email notification sent to {}", email);
        } catch (Exception e) {
            log.error("Failed to send email notification to {}", email, e);
        }
    }

    @Override
    public List<NotificationResponse> getAll() {
        return notificationRepository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private NotificationResponse mapToResponse(Notification notification) {
        return NotificationResponse.builder()
                .notificationId(notification.getNotificationId())
                .recipientId(notification.getRecipientId())
                .actorId(notification.getActorId())
                .type(notification.getType())
                .title(notification.getTitle())
                .message(notification.getMessage())
                .relatedId(notification.getRelatedId())
                .relatedType(notification.getRelatedType())
                .relatedSlug(notification.getRelatedSlug())
                .isRead(notification.isRead())
                .createdAt(notification.getCreatedAt())
                .build();
    }
}
