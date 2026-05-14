package com.inkwell.post.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.PAYMENT_REQUIRED)
public class PremiumContentLockedException extends RuntimeException {
    public PremiumContentLockedException(String message) {
        super(message);
    }
}
