package com.inkwell.payment.service;

import com.inkwell.payment.dto.PaymentOrderRequest;
import com.inkwell.payment.dto.PaymentResponse;
import com.inkwell.payment.dto.PaymentVerifyRequest;
import java.util.List;

public interface PaymentService {
    String createOrder(PaymentOrderRequest request) throws Exception;
    PaymentResponse verifyPayment(PaymentVerifyRequest request) throws Exception;
    boolean hasAccess(String userId, String postId);
    List<PaymentResponse> getUserPayments(String userId);
}
