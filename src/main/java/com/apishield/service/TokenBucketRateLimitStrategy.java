package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Clock;
import java.util.List;

public class TokenBucketRateLimitStrategy implements RateLimitStrategy {

    private final StringRedisTemplate redisTemplate;
    private final RedisScript<List> tokenBucketScript;

    public TokenBucketRateLimitStrategy(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;

        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("scripts/token-bucket.lua"));
        script.setResultType(List.class);
        this.tokenBucketScript = script;
    }

    @Override
    public RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock) {
        long now = clock.instant().getEpochSecond();
        String key = "rate_limit:bucket:" + apiKey;

        List<?> redisResult = redisTemplate.execute(
                tokenBucketScript,
                List.of(key),
                String.valueOf(requestLimit),
                String.valueOf(windowSeconds),
                String.valueOf(now)
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
