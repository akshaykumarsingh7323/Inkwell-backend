package com.inkwell.payment.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentOrderRequest {
    private String userId;
    private String postId;
    private double amount;
    private String paymentType; // SINGLE_POST or SUBSCRIPTION
}
