package com.apishield.service;

import com.apishield.dto.RateLimitResult;

import java.time.Clock;

@FunctionalInterface
public interface RateLimitStrategy {

    RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock);
}
