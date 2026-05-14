package com.inkwell.category.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CategoryResponse {
    private Long categoryId;
    private String name;
    private String slug;
    private String description;
    private Long parentCategoryId;
    private Long postCount;
    private LocalDateTime createdAt;
}
