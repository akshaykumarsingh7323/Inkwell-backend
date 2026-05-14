package com.inkwell.auth.service.impl;

import com.inkwell.auth.service.OtpService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("test")
@Slf4j
public class MockOtpServiceImpl implements OtpService {

    @Override
    public void sendOtp(String phoneNumber) {
        log.info("[MOCK] Sending OTP to {}", phoneNumber);
    }

    @Override
    public boolean verifyOtp(String phoneNumber, String code) {
        log.info("[MOCK] Verifying OTP for {} with code {}", phoneNumber, code);
        return "123456".equals(code); // Accept 123456 as a valid mock code in tests
    }
}
