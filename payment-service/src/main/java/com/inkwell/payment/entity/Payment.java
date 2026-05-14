package com.inkwell.payment.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @Column(length = 36)
    private String paymentId;

    @Column(nullable = false)
    private String userId;

    @Column(nullable = false)
    private String postId;

    @Column(nullable = false)
    private double amount;

    @Enumerated(EnumType.STRING)
    private PaymentStatus status;

    private String orderId;
    
    @Builder.Default
    private String paymentProvider = "RAZORPAY";

    @Enumerated(EnumType.STRING)
    @Builder.Default
    private PaymentType paymentType = PaymentType.SINGLE_POST;

    private String authorId;
    private double totalAmount;
    private double adminCommission;
    private double authorEarning;

    private LocalDateTime createdAt;

    public enum PaymentStatus {
        PENDING, SUCCESS, FAILED
    }

    public enum PaymentType {
        SINGLE_POST, SUBSCRIPTION
    }
}
