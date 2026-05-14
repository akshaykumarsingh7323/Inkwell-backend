package com.inkwell.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostRequest {
    
    @NotBlank(message = "Title is required")
    @Size(min = 5, max = 200, message = "Title must be between 5 and 200 characters")
    private String title;
    
    @NotBlank(message = "Content is required")
    @Size(min = 10, message = "Content must be at least 10 characters long")
    private String content;
    
    @Size(max = 500, message = "Excerpt cannot exceed 500 characters")
    private String excerpt;
    
    private String featuredImageUrl;
    private Long featuredImageMediaId;
    private Long categoryId;
    private List<Long> tagIds;
    
    @JsonProperty("isPremium")
    private boolean isPremium;
    
    private Double price;
}
