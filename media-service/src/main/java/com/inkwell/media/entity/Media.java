package com.inkwell.media.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "media")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Media {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long mediaId;

    @Column(nullable = false)
    private Long uploaderId;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String originalName;

    @Column(nullable = false)
    private String url;

    private String mimeType;

    private Long sizeKb;

    private String altText;

    private Long linkedPostId;

    @Builder.Default
    private boolean isDeleted = false;

    @CreationTimestamp
    private LocalDateTime uploadedAt;
}
