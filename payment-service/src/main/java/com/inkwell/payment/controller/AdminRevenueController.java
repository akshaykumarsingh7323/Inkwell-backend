package com.inkwell.payment.controller;

import com.inkwell.payment.entity.AuthorEarning;
import com.inkwell.payment.entity.Payment;
import com.inkwell.payment.repository.AuthorEarningRepository;
import com.inkwell.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/payments/admin")
public class AdminRevenueController {

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private AuthorEarningRepository authorEarningRepository;

    @GetMapping("/revenue/total")
    public ResponseEntity<Map<String, Object>> getTotalRevenue() {
        List<Payment> successPayments = paymentRepository.findAll().stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCESS)
                .toList();
        
        double totalVolume = successPayments.stream().mapToDouble(Payment::getTotalAmount).sum();
        double totalCommission = successPayments.stream().mapToDouble(Payment::getAdminCommission).sum();
        
        Map<String, Object> response = new HashMap<>();
        response.put("totalRevenue", totalCommission); // Admin's cut
        response.put("totalVolume", totalVolume);    // Total throughput
        response.put("transactionCount", successPayments.size());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/revenue/top-authors")
    public ResponseEntity<List<AuthorEarning>> getTopAuthors() {
        return ResponseEntity.ok(authorEarningRepository.findAll());
    }

    @GetMapping("/revenue/transactions")
    public ResponseEntity<List<Payment>> getRecentTransactions() {
        return ResponseEntity.ok(paymentRepository.findAll());
    }
}
