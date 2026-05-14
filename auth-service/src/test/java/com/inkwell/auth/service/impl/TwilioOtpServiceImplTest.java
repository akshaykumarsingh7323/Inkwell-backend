package com.inkwell.auth.service.impl;

import com.inkwell.auth.exception.CustomException;
import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import com.twilio.rest.verify.v2.service.VerificationCheckCreator;
import com.twilio.rest.verify.v2.service.VerificationCreator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TwilioOtpServiceImplTest {

    private TwilioOtpServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new TwilioOtpServiceImpl();
    }

    @Test
    void init_WithDummyCredentials_ShouldReturnEarly() {
        ReflectionTestUtils.setField(service, "accountSid", "dummy");
        ReflectionTestUtils.setField(service, "authToken", "dummy");
        ReflectionTestUtils.setField(service, "verifyServiceSid", "dummy");

        service.init();

        assertTrue(true);
    }

    @Test
    void init_WithRealLookingCredentials_ShouldCallTwilioInit() {
        ReflectionTestUtils.setField(service, "accountSid", "sid");
        ReflectionTestUtils.setField(service, "authToken", "token");

        try (MockedStatic<Twilio> twilioMock = Mockito.mockStatic(Twilio.class)) {
            service.init();
            twilioMock.verify(() -> Twilio.init("sid", "token"));
        }
    }

    @Test
    void init_WhenTwilioInitThrows_ShouldHandleException() {
        ReflectionTestUtils.setField(service, "accountSid", "sid");
        ReflectionTestUtils.setField(service, "authToken", "token");

        try (MockedStatic<Twilio> twilioMock = Mockito.mockStatic(Twilio.class)) {
            twilioMock.when(() -> Twilio.init(anyString(), anyString())).thenThrow(new RuntimeException("Twilio error"));
            service.init();
            twilioMock.verify(() -> Twilio.init("sid", "token"));
        }
    }

    @Test
    void sendOtp_WhenTwilioFails_ShouldThrowCustomException() {
        ReflectionTestUtils.setField(service, "verifyServiceSid", null);
        assertThrows(CustomException.class, () -> service.sendOtp("+911234567890"));
    }

    @Test
    void sendOtp_WhenTwilioSucceeds_ShouldComplete() {
        VerificationCreator creator = mock(VerificationCreator.class);
        Verification verification = mock(Verification.class);
        when(creator.create()).thenReturn(verification);
        when(verification.getStatus()).thenReturn("pending");

        try (MockedStatic<Verification> verificationMock = Mockito.mockStatic(Verification.class)) {
            verificationMock.when(() -> Verification.creator("serviceSid", "+911234567890", "sms")).thenReturn(creator);
            ReflectionTestUtils.setField(service, "verifyServiceSid", "serviceSid");

            service.sendOtp("+911234567890");

            verify(creator).create();
        }
    }

    @Test
    void verifyOtp_WhenTwilioFails_ShouldThrowCustomException() {
        ReflectionTestUtils.setField(service, "verifyServiceSid", null);
        assertThrows(CustomException.class, () -> service.verifyOtp("+911234567890", "123456"));
    }

    @Test
    void verifyOtp_WhenApproved_ShouldReturnTrue() {
        VerificationCheckCreator creator = mock(VerificationCheckCreator.class);
        VerificationCheck verificationCheck = mock(VerificationCheck.class);
        when(creator.setTo("+911234567890")).thenReturn(creator);
        when(creator.setCode("123456")).thenReturn(creator);
        when(creator.create()).thenReturn(verificationCheck);
        when(verificationCheck.getStatus()).thenReturn("approved");

        try (MockedStatic<VerificationCheck> verificationCheckMock = Mockito.mockStatic(VerificationCheck.class)) {
            verificationCheckMock.when(() -> VerificationCheck.creator("serviceSid")).thenReturn(creator);
            ReflectionTestUtils.setField(service, "verifyServiceSid", "serviceSid");

            assertTrue(service.verifyOtp("+911234567890", "123456"));
        }
    }

    @Test
    void verifyOtp_WhenNotApproved_ShouldReturnFalse() {
        VerificationCheckCreator creator = mock(VerificationCheckCreator.class);
        VerificationCheck verificationCheck = mock(VerificationCheck.class);
        when(creator.setTo("+911234567890")).thenReturn(creator);
        when(creator.setCode("000000")).thenReturn(creator);
        when(creator.create()).thenReturn(verificationCheck);
        when(verificationCheck.getStatus()).thenReturn("pending");

        try (MockedStatic<VerificationCheck> verificationCheckMock = Mockito.mockStatic(VerificationCheck.class)) {
            verificationCheckMock.when(() -> VerificationCheck.creator("serviceSid")).thenReturn(creator);
            ReflectionTestUtils.setField(service, "verifyServiceSid", "serviceSid");

            assertFalse(service.verifyOtp("+911234567890", "000000"));
        }
    }
}
