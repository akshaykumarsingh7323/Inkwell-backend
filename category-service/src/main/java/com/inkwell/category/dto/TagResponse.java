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
public class TagResponse {
    private Long tagId;
    private String name;
    private String slug;
    private Long postCount;
    private LocalDateTime createdAt;
}
