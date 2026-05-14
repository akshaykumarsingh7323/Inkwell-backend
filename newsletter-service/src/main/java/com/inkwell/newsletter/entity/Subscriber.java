package com.inkwell.newsletter.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "subscribers", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"email", "followedAuthorId"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscriber {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long subscriberId;

    @Column(nullable = false)
    private String email;

    private Long userId;

    private Long followedAuthorId;

    private String fullName;

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private SubscriberStatus status = SubscriberStatus.PENDING;

    @CreationTimestamp
    private LocalDateTime subscribedAt;

    private LocalDateTime unsubscribedAt;

    /** Unique token used for confirmation and one-click unsubscribe. */
    @Column(unique = true, nullable = false)
    private String token;

    /**
     * Expiry timestamp for the confirmation token.
     * Set to 48 hours after token generation.
     */
    private LocalDateTime tokenExpiresAt;

    /**
     * Comma-separated preference tags, e.g. "TECH,BUSINESS,DESIGN".
     * Used for targeted campaign filtering.
     */
    @Column(columnDefinition = "TEXT")
    private String preferences;
}

