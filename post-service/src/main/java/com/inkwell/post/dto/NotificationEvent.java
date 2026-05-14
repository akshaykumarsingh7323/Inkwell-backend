package com.inkwell.post.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent implements Serializable {
    private Long userId; // Recipient
    private String type; // NEW_COMMENT, COMMENT_REPLY, LIKE, etc.
    private String message;
    private Map<String, String> metadata;
}
