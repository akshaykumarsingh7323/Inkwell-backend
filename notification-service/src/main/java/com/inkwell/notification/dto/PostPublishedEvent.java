package com.inkwell.notification.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostPublishedEvent {
    private Long postId;
    private Long authorId;
    private String title;
    private String slug;
}
