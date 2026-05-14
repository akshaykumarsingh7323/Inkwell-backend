package com.inkwell.media.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MediaResponse {
    private Long mediaId;
    private Long uploaderId;
    private String filename;
    private String originalName;
    private String url;
    private String mimeType;
    private Long sizeKb;
    private String altText;
    private Long linkedPostId;
    private boolean isDeleted;
    private LocalDateTime uploadedAt;
}
