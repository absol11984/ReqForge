package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

public class SlidingWindowRateLimitStrategy implements RateLimitStrategy {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> slidingWindowScript;

    public SlidingWindowRateLimitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/sliding-window.lua"));
        // Spring maps Lua table returns to a java.util.List
        script.setResultType(List.class);
        this.slidingWindowScript = script;
    }

    @Override
    public RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock) {
        long now = clock.instant().getEpochSecond();
        String key = "rate_limit:sliding:" + apiKey;

        // Unique member id per request
        String memberId = UUID.randomUUID().toString();

        List<?> redisResult = redisTemplate.execute(
                slidingWindowScript,
                List.of(key),
                String.valueOf(now),
                String.valueOf(windowSeconds),
                String.valueOf(requestLimit),
                memberId
        );

        long allowed = extractLong(redisResult, 0);
        long remaining = extractLong(redisResult, 1);
        long resetSeconds = extractLongRounded(redisResult, 2);

        return new RateLimitResult(allowed == 1, requestLimit, remaining, resetSeconds);
    }

    private long extractLong(List<?> redisResult, int index) {
        if (redisResult == null || redisResult.size() <= index || redisResult.get(index) == null) {
            return 0;
        }
        Object v = redisResult.get(index);
        if (v instanceof Number n) {
            return n.longValue();
        }
        return Long.parseLong(String.valueOf(v));
    }

    private long extractLongRounded(List<?> redisResult, int index) {
        if (redisResult == null || redisResult.size() <= index || redisResult.get(index) == null) {
            return 0;
        }
        Object v = redisResult.get(index);
        if (v instanceof Number n) {
            return Math.round(n.doubleValue());
        }
        return Math.round(Double.parseDouble(String.valueOf(v)));
    }
}
