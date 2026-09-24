package com.apishield.dto;

public record RateLimitResult(
        boolean allowed,
        long limit,
        long remaining,
        long resetSeconds
) {}
