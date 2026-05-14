package com.inkwell.auth.service.impl;

import com.inkwell.auth.exception.CustomException;
import com.inkwell.auth.service.OtpService;
import com.twilio.Twilio;
import com.twilio.rest.verify.v2.service.Verification;
import com.twilio.rest.verify.v2.service.VerificationCheck;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@Profile("!test")
@Slf4j
public class TwilioOtpServiceImpl implements OtpService {

    @Value("${twilio.account-sid}")
    private String accountSid;

    @Value("${twilio.auth-token}")
    private String authToken;

    @Value("${twilio.verify-service-sid}")
    private String verifyServiceSid;

    @PostConstruct
    public void init() {
        if ("dummy".equals(accountSid)) {
            log.warn("Twilio dummy credentials loaded. SMS will not work.");
            return;
        }
        try {
            Twilio.init(accountSid, authToken);
            log.info("Twilio initialized for OTP service");
        } catch (Exception e) {
            log.error("Failed to initialize Twilio: {}", e.getMessage());
        }
    }

    @Override
    public void sendOtp(String phoneNumber) {
        try {
            Verification verification = Verification.creator(verifyServiceSid, phoneNumber, "sms").create();
            log.info("OTP sent successfully to {}, Status: {}", phoneNumber, verification.getStatus());
        } catch (Exception e) {
            log.error("Error sending OTP to {}: {}", phoneNumber, e.getMessage());
            throw new CustomException("Failed to send OTP: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

    @Override
    public boolean verifyOtp(String phoneNumber, String code) {
        try {
            VerificationCheck verificationCheck = VerificationCheck.creator(verifyServiceSid)
                    .setTo(phoneNumber)
                    .setCode(code)
                    .create();
            
            boolean isApproved = "approved".equalsIgnoreCase(verificationCheck.getStatus());
            if (isApproved) {
                log.info("OTP verified successfully for {}", phoneNumber);
            } else {
                log.warn("Invalid OTP for {}, Status: {}", phoneNumber, verificationCheck.getStatus());
            }
            return isApproved;
        } catch (Exception e) {
            log.error("Error verifying OTP for {}: {}", phoneNumber, e.getMessage());
            throw new CustomException("Failed to verify OTP: " + e.getMessage(), HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }
}
