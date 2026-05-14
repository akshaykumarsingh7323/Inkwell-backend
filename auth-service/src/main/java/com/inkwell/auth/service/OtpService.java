package com.inkwell.auth.service;

public interface OtpService {
    void sendOtp(String phoneNumber);
    boolean verifyOtp(String phoneNumber, String code);
}
