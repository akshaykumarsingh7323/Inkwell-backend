package com.inkwell.newsletter.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostPublishedEvent implements Serializable {
    private Long postId;
    private Long authorId;
    private String title;
    private String slug;
}
