package com.inkwell.payment.controller;

import com.inkwell.payment.entity.AuthorEarning;
import com.inkwell.payment.entity.Payment;
import com.inkwell.payment.repository.AuthorEarningRepository;
import com.inkwell.payment.repository.PaymentRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/payments/earnings")
public class EarningController {

    @Autowired
    private AuthorEarningRepository authorEarningRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @GetMapping("/author/{authorId}")
    public ResponseEntity<AuthorEarning> getAuthorEarnings(@PathVariable String authorId) {
        return ResponseEntity.ok(authorEarningRepository.findById(authorId)
                .orElse(AuthorEarning.builder().authorId(authorId).totalEarnings(BigDecimal.ZERO).build()));
    }

    @GetMapping("/author/{authorId}/transactions")
    public ResponseEntity<List<Payment>> getAuthorTransactions(@PathVariable String authorId) {
        return ResponseEntity.ok(paymentRepository.findByAuthorId(authorId).stream()
                .filter(p -> p.getStatus() == Payment.PaymentStatus.SUCCESS)
                .toList());
    }
}
