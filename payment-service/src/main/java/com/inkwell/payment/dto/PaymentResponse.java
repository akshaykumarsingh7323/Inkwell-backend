package com.inkwell.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentResponse {
    private String paymentId;
    private String userId;
    private String postId;
    private double amount;
    private double totalAmount;
    private double adminCommission;
    private double authorEarning;
    private String authorId;
    private String status;
    private String orderId;
    private LocalDateTime createdAt;
}
