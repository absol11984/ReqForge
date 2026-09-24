package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Service
public class RateLimitService {

    private final StringRedisTemplate redisTemplate;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public RateLimitResult checkRateLimit(String apiKey, int requestLimit, int windowSeconds) {
        long epochSeconds = Instant.now().getEpochSecond();
        long windowId = epochSeconds / windowSeconds;
        long resetSeconds = ((windowId + 1) * windowSeconds) - epochSeconds;

        String key = "rate_limit:" + apiKey + ":" + windowId;

        Long currentCount = redisTemplate.opsForValue().increment(key);
        if (currentCount != null && currentCount == 1) {
            redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));
        }

        long count = (currentCount != null) ? currentCount : 1;
        boolean allowed = count <= requestLimit;
        long remaining = allowed ? Math.max(0, requestLimit - count) : 0;

        return new RateLimitResult(allowed, requestLimit, remaining, resetSeconds);
    }
}
