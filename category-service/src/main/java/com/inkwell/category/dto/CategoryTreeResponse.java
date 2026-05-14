package com.inkwell.category.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryTreeResponse {
    private Long categoryId;
    private String name;
    private String slug;
    private String description;
    private Long postCount;
    private List<CategoryTreeResponse> children;
}
