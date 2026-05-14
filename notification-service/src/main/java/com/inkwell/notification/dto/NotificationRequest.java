package com.inkwell.notification.dto;

import com.inkwell.notification.enums.NotificationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationRequest {
    @NotNull(message = "Recipient ID is required")
    private Long recipientId;
    
    @NotNull(message = "Actor ID is required")
    private Long actorId;
    
    @NotNull(message = "Notification type is required")
    private NotificationType type;
    
    @NotBlank(message = "Title is required")
    private String title;
    
    @NotBlank(message = "Message is required")
    private String message;
    
    private Long relatedId;
    private String relatedType;
    private String relatedSlug;
    
    private boolean sendEmail;
    private String recipientEmail;
}
