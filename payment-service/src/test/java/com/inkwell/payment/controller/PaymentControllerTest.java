package com.inkwell.payment.controller;

import com.inkwell.payment.dto.PaymentOrderRequest;
import com.inkwell.payment.dto.PaymentResponse;
import com.inkwell.payment.dto.PaymentVerifyRequest;
import com.inkwell.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentControllerTest {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentController paymentController;

    @Test
    void createOrder_ShouldReturnOrderId() throws Exception {
        PaymentOrderRequest request = new PaymentOrderRequest();
        when(paymentService.createOrder(request)).thenReturn("order-123");

        assertEquals("order-123", paymentController.createOrder(request).getBody());
    }

    @Test
    void verifyPayment_ShouldReturnResponse() throws Exception {
        PaymentVerifyRequest request = new PaymentVerifyRequest();
        PaymentResponse response = new PaymentResponse();
        when(paymentService.verifyPayment(request)).thenReturn(response);

        assertEquals(response, paymentController.verifyPayment(request).getBody());
    }

    @Test
    void checkAccess_ShouldReturnFlag() {
        when(paymentService.hasAccess("1", "2")).thenReturn(true);

        assertEquals(true, paymentController.checkAccess("1", "2").getBody());
    }

    @Test
    void getUserPayments_ShouldReturnPayments() {
        List<PaymentResponse> responses = List.of(new PaymentResponse());
        when(paymentService.getUserPayments("1")).thenReturn(responses);

        assertEquals(responses, paymentController.getUserPayments("1").getBody());
    }
}
