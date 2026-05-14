package com.inkwell.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class VerifyOtpRequest {
    @NotBlank(message = "Phone number is required")
    @Pattern(regexp = "^\\+[1-9]\\d{1,14}$", message = "Invalid phone number format (must include country code, e.g., +1234567890)")
    private String phoneNumber;
    
    @NotBlank(message = "OTP code is required")
    @Pattern(regexp = "^\\d{4,6}$", message = "OTP code must be 4 to 6 digits")
    private String code;
}
