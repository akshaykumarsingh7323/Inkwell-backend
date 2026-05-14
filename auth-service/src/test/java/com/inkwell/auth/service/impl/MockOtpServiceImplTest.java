package com.inkwell.auth.service.impl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MockOtpServiceImplTest {

    private final MockOtpServiceImpl service = new MockOtpServiceImpl();

    @Test
    void sendOtp_ShouldNotThrow() {
        service.sendOtp("+911234567890");
        assertTrue(true);
    }

    @Test
    void verifyOtp_WithExpectedCode_ShouldReturnTrue() {
        assertTrue(service.verifyOtp("+911234567890", "123456"));
    }

    @Test
    void verifyOtp_WithUnexpectedCode_ShouldReturnFalse() {
        assertFalse(service.verifyOtp("+911234567890", "000000"));
    }
}
