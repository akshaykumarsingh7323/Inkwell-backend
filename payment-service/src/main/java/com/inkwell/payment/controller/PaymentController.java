package com.inkwell.payment.controller;

import com.inkwell.payment.dto.PaymentOrderRequest;
import com.inkwell.payment.dto.PaymentResponse;
import com.inkwell.payment.dto.PaymentVerifyRequest;
import com.inkwell.payment.service.PaymentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    @Autowired
    private PaymentService paymentService;

    @PostMapping("/create-order")
    public ResponseEntity<String> createOrder(@RequestBody PaymentOrderRequest request) throws Exception {
        return ResponseEntity.ok(paymentService.createOrder(request));
    }

    @PostMapping("/verify")
    public ResponseEntity<PaymentResponse> verifyPayment(@RequestBody PaymentVerifyRequest request) throws Exception {
        return ResponseEntity.ok(paymentService.verifyPayment(request));
    }

    @GetMapping("/check")
    public ResponseEntity<Boolean> checkAccess(@RequestParam String userId, @RequestParam String postId) {
        return ResponseEntity.ok(paymentService.hasAccess(userId, postId));
    }

    @GetMapping("/user/{userId}")
    public ResponseEntity<List<PaymentResponse>> getUserPayments(@PathVariable String userId) {
        return ResponseEntity.ok(paymentService.getUserPayments(userId));
    }
}
