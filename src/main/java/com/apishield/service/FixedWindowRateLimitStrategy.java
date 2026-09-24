package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

public class FixedWindowRateLimitStrategy implements RateLimitStrategy {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<Long> fixedWindowScript;

    public FixedWindowRateLimitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/fixed-window.lua"));
        script.setResultType(Long.class);
        this.fixedWindowScript = script;
    }

    @Override
    public RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock) {
        long epochSeconds = clock.instant().getEpochSecond();
        long windowId = epochSeconds / windowSeconds;
        long resetSeconds = ((windowId + 1) * windowSeconds) - epochSeconds;

        String key = "rate_limit:fixed:" + apiKey + ":" + windowId;

        // Atomic increment + conditional expiry initialization
        Long currentCount = redisTemplate.execute(
                fixedWindowScript,
                List.of(key),
                String.valueOf(windowSeconds)
        );

        long count = currentCount != null ? currentCount : 1L;
        boolean allowed = count <= requestLimit;
        long remaining = allowed ? Math.max(0, requestLimit - count) : 0;

        return new RateLimitResult(allowed, requestLimit, remaining, Math.max(0, resetSeconds));
    }
}
