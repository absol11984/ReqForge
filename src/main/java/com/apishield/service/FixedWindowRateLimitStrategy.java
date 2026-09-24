package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Duration;

public class FixedWindowRateLimitStrategy implements RateLimitStrategy {

    private final StringRedisTemplate redisTemplate;

    public FixedWindowRateLimitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock) {
        long epochSeconds = clock.instant().getEpochSecond();
        long windowId = epochSeconds / windowSeconds;
        long resetSeconds = ((windowId + 1) * windowSeconds) - epochSeconds;
        String key = "rate_limit:fixed:" + apiKey + ":" + windowId;

        ValueOperations<String, String> operations = redisTemplate.opsForValue();
        Long currentCount = operations.increment(key);
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        long count = currentCount != null ? currentCount : 1;
        boolean allowed = count <= requestLimit;
        long remaining = allowed ? Math.max(0, requestLimit - count) : 0;
        return new RateLimitResult(allowed, requestLimit, remaining, resetSeconds);
    }
}
