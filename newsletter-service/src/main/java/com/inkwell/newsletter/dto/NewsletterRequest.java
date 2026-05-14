package com.inkwell.newsletter.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class NewsletterRequest {
    @NotBlank(message = "Subject is required")
    private String subject;
    @NotBlank(message = "Content is required")
    private String content;
}
