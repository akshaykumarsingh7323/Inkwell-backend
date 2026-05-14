package com.inkwell.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for admin role-based broadcast notifications.
 */
@Data
@NoArgsConstructor
public class BroadcastRequest {

    @NotBlank(message = "Target role is required")
    @Pattern(regexp = "READER|AUTHOR|ADMIN|ALL",
             message = "Target role must be one of: READER, AUTHOR, ADMIN, ALL")
    private String targetRole;

    @NotBlank(message = "Title is required")
    private String title;

    @NotBlank(message = "Message is required")
    private String message;
}
