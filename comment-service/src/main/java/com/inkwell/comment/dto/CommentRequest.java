package com.inkwell.comment.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommentRequest {
    @NotNull(message = "Post ID is required")
    private Long postId;

    private Long parentId;

    private Long authorId;

    @NotBlank(message = "Comment content is required")
    private String content;
}
