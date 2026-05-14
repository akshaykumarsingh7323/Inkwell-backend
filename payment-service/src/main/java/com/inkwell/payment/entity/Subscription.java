package com.inkwell.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String subscriptionId;

    @Column(nullable = false)
    private String userId;

    private String plan; // e.g., MONTHLY, YEARLY

    private LocalDateTime startDate;
    private LocalDateTime expiryDate;

    @Enumerated(EnumType.STRING)
    private SubscriptionStatus status;

    public enum SubscriptionStatus {
        ACTIVE, EXPIRED, CANCELLED
    }

    public boolean isActive() {
        return status == SubscriptionStatus.ACTIVE && (expiryDate == null || expiryDate.isAfter(LocalDateTime.now()));
    }
}
