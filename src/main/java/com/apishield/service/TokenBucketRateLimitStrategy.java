package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.util.Map;

public class TokenBucketRateLimitStrategy implements RateLimitStrategy {

    private static final String TOKENS_FIELD = "tokens";
    private static final String LAST_REFILL_FIELD = "lastRefillSeconds";

    private final StringRedisTemplate redisTemplate;

    public TokenBucketRateLimitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock) {
        long now = clock.instant().getEpochSecond();
        String key = "rate_limit:bucket:" + apiKey;
        HashOperations<String, String, String> operations = redisTemplate.opsForHash();

        Double available = parseDouble(operations.get(key, TOKENS_FIELD));
        Double lastRefill = parseDouble(operations.get(key, LAST_REFILL_FIELD));
        if (available == null || lastRefill == null) {
            available = (double) requestLimit;
            lastRefill = (double) now;
        } else {
            double elapsedSeconds = Math.max(0, now - lastRefill);
            double refillRate = requestLimit / (double) windowSeconds;
            available = Math.min(requestLimit, available + elapsedSeconds * refillRate);
        }

        boolean allowed = available >= 1;
        if (allowed) {
            available -= 1;
        }
        operations.put(key, TOKENS_FIELD, Double.toString(available));
        operations.put(key, LAST_REFILL_FIELD, Long.toString(now));

        long remaining = (long) Math.floor(available);
        long resetSeconds = allowed ? 0 : estimatedRefillSeconds(1 - available, requestLimit, windowSeconds);
        return new RateLimitResult(allowed, requestLimit, remaining, resetSeconds);
    }

    private long estimatedRefillSeconds(double missingTokens, int requestLimit, int windowSeconds) {
        double refillRate = requestLimit / (double) windowSeconds;
        return Math.max(1, (long) Math.ceil(missingTokens / refillRate));
    }

    private Double parseDouble(String value) {
        return value == null ? null : Double.parseDouble(value);
    }
}
