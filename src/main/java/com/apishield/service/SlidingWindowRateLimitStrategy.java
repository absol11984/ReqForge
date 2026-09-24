package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;

public class SlidingWindowRateLimitStrategy implements RateLimitStrategy {

    private final StringRedisTemplate redisTemplate;

    public SlidingWindowRateLimitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Override
    public RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock) {
        long now = clock.instant().getEpochSecond();
        String key = "rate_limit:sliding:" + apiKey;
        ZSetOperations<String, String> operations = redisTemplate.opsForZSet();

        Set<String> oldRequests = operations.rangeByScore(key, Double.NEGATIVE_INFINITY, now - windowSeconds);
        if (oldRequests != null && !oldRequests.isEmpty()) {
            operations.remove(key, oldRequests);
        }

        Long size = operations.size(key);
        long count = size != null ? size : 0;

        boolean allowed = count < requestLimit;

        // For remaining we want: limit - (count if allowed==false else count+1)
        long updatedCount = allowed ? count + 1 : count;

        if (allowed) {
            operations.add(key, UUID.randomUUID().toString(), now);
        }

        long remaining = Math.max(0, requestLimit - updatedCount);
        long resetSeconds = updatedCount > 0
                ? resetTime(operations, key, now, windowSeconds)
                : windowSeconds;
        redisTemplate.expire(key, Duration.ofSeconds(windowSeconds));

        return new RateLimitResult(allowed, requestLimit, remaining, resetSeconds);
    }

    private long resetTime(ZSetOperations<String, String> operations, String key,
                           long now, int windowSeconds) {
        Set<String> members = operations.range(key, 0, 0);
        if (members == null || members.isEmpty()) {
            return windowSeconds;
        }
        Double oldestScore = operations.score(key, members.iterator().next());
        if (oldestScore == null) {
            return windowSeconds;
        }
        return Math.max(1, Math.round(oldestScore + windowSeconds - now));
    }
}
