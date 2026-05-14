package com.inkwell.post.dto;

import com.inkwell.post.enums.PostStatus;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostResponse {
    private Long postId;
    private Long authorId;
    private Long categoryId;
    private String title;
    private String slug;
    private String content;
    private String excerpt;
    private String featuredImageUrl;
    private PostStatus status;
    private Integer readTimeMin;
    private Long viewCount;
    private Long likesCount;
    private Long commentsCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime publishedAt;
    
    @JsonProperty("isPremium")
    private boolean isPremium;
    
    private Double price;
    
    @JsonProperty("accessUnlocked")
    private boolean accessUnlocked;
    
    private List<Long> tagIds;
    
    // UI convenience field
    private boolean isLikedByCurrentUser;
}
