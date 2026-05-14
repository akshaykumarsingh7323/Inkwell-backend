package com.inkwell.payment.controller;

import com.inkwell.payment.entity.AuthorEarning;
import com.inkwell.payment.entity.Payment;
import com.inkwell.payment.repository.AuthorEarningRepository;
import com.inkwell.payment.repository.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminRevenueControllerTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private AuthorEarningRepository authorEarningRepository;

    @InjectMocks
    private AdminRevenueController adminRevenueController;

    @Test
    void getTotalRevenue_ShouldAggregateSuccessfulPaymentsOnly() {
        Payment success = Payment.builder()
                .status(Payment.PaymentStatus.SUCCESS)
                .totalAmount(100.0)
                .adminCommission(10.0)
                .build();
        Payment failed = Payment.builder()
                .status(Payment.PaymentStatus.FAILED)
                .totalAmount(50.0)
                .adminCommission(5.0)
                .build();
        when(paymentRepository.findAll()).thenReturn(List.of(success, failed));

        Map<String, Object> body = adminRevenueController.getTotalRevenue().getBody();
        assertEquals(10.0, body.get("totalRevenue"));
        assertEquals(100.0, body.get("totalVolume"));
        assertEquals(1, body.get("transactionCount"));
    }

    @Test
    void getTopAuthors_ShouldReturnRepositoryResults() {
        List<AuthorEarning> earnings = List.of(AuthorEarning.builder().authorId("1").totalEarnings(BigDecimal.TEN).build());
        when(authorEarningRepository.findAll()).thenReturn(earnings);

        assertEquals(earnings, adminRevenueController.getTopAuthors().getBody());
    }

    @Test
    void getRecentTransactions_ShouldReturnRepositoryResults() {
        List<Payment> payments = List.of(Payment.builder().build());
        when(paymentRepository.findAll()).thenReturn(payments);

        assertEquals(payments, adminRevenueController.getRecentTransactions().getBody());
    }
}
