package com.apishield.exception;

import com.apishield.dto.RateLimitResult;

public class RateLimitExceededException extends RuntimeException {
    private final RateLimitResult rateLimitResult;

    public RateLimitExceededException(String message, RateLimitResult rateLimitResult) {
        super(message);
        this.rateLimitResult = rateLimitResult;
    }

    public RateLimitResult getRateLimitResult() {
        return rateLimitResult;
    }
}
