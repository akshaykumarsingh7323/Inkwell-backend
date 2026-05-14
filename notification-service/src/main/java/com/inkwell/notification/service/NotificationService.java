package com.inkwell.notification.service;

import com.inkwell.notification.dto.NotificationRequest;
import com.inkwell.notification.dto.NotificationResponse;

import java.util.List;

public interface NotificationService {
    NotificationResponse send(NotificationRequest request);
    void sendBulk(List<Long> recipientIds, NotificationRequest request);

    /** Convenience wrapper: builds the request and sends to a list of recipients. */
    void sendBulkNotification(List<Long> recipientIds, String title, String message, String type);

    /** Sends to ALL users in the system (no role filter). */
    void sendBroadcastToAll(String title, String message);

    void markAsRead(Long notificationId);
    void markAllRead(Long recipientId);
    void deleteRead(Long recipientId);
    List<NotificationResponse> getByRecipient(Long recipientId);
    long getUnreadCount(Long recipientId);
    void deleteNotification(Long notificationId);
    NotificationResponse getNotification(Long notificationId);
    void sendEmail(String email, String subject, String message);
    List<NotificationResponse> getAll();
    void notifyAdmins(String title, String message, Long relatedId, String relatedSlug);
}

