package com.inkwell.post.entity;

import com.inkwell.post.enums.PostStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "posts", indexes = {
    @Index(name = "idx_post_slug", columnList = "slug"),
    @Index(name = "idx_post_author", columnList = "authorId"),
    @Index(name = "idx_post_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long postId;

    @Column(nullable = false)
    private Long authorId;

    private Long categoryId;

    @Column(nullable = false)
    private String title;

    @Column(unique = true, nullable = false)
    private String slug;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content;

    private String excerpt;

    private String featuredImageUrl;

    @Enumerated(EnumType.STRING)
    private PostStatus status;

    private Integer readTimeMin;

    @Builder.Default
    private Long viewCount = 0L;

    @Builder.Default
    private Long likesCount = 0L;

    @Builder.Default
    private Long commentsCount = 0L;

    @CreationTimestamp
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;

    @Builder.Default
    private boolean isPremium = false;

    private Double price;

    @Builder.Default
    private Long dailyViewCount = 0L;

    private LocalDate lastViewDate;

    private LocalDateTime publishedAt;
}