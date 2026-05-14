package com.inkwell.notification.dto;

import com.inkwell.notification.enums.NotificationType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationResponse {
    private Long notificationId;
    private Long recipientId;
    private Long actorId;
    private NotificationType type;
    private String title;
    private String message;
    private Long relatedId;
    private String relatedType;
    private String relatedSlug;
    private boolean isRead;
    private LocalDateTime createdAt;
}
