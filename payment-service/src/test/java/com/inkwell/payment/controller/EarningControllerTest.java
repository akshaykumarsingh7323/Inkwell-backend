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
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EarningControllerTest {

    @Mock
    private AuthorEarningRepository authorEarningRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @InjectMocks
    private EarningController earningController;

    @Test
    void getAuthorEarnings_WhenPresent_ShouldReturnExistingValue() {
        AuthorEarning earning = AuthorEarning.builder().authorId("1").totalEarnings(BigDecimal.TEN).build();
        when(authorEarningRepository.findById("1")).thenReturn(Optional.of(earning));

        assertEquals(earning, earningController.getAuthorEarnings("1").getBody());
    }

    @Test
    void getAuthorEarnings_WhenMissing_ShouldReturnZeroEarnings() {
        when(authorEarningRepository.findById("1")).thenReturn(Optional.empty());

        AuthorEarning body = earningController.getAuthorEarnings("1").getBody();
        assertEquals("1", body.getAuthorId());
        assertEquals(BigDecimal.ZERO, body.getTotalEarnings());
    }

    @Test
    void getAuthorTransactions_ShouldFilterOnlySuccessfulPayments() {
        Payment success = Payment.builder().status(Payment.PaymentStatus.SUCCESS).build();
        Payment failed = Payment.builder().status(Payment.PaymentStatus.FAILED).build();
        when(paymentRepository.findByAuthorId("1")).thenReturn(List.of(success, failed));

        assertEquals(List.of(success), earningController.getAuthorTransactions("1").getBody());
    }
}
