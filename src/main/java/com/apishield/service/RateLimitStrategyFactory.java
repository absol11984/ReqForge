package com.apishield.service;

import com.apishield.entity.RateLimitAlgorithm;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RateLimitStrategyFactory {

    private final Map<RateLimitAlgorithm, RateLimitStrategy> strategies;

    public RateLimitStrategyFactory(StringRedisTemplate redisTemplate) {
        strategies = Map.of(
                RateLimitAlgorithm.FIXED_WINDOW, new FixedWindowRateLimitStrategy(redisTemplate),
                RateLimitAlgorithm.SLIDING_WINDOW, new SlidingWindowRateLimitStrategy(redisTemplate),
                RateLimitAlgorithm.TOKEN_BUCKET, new TokenBucketRateLimitStrategy(redisTemplate)
        );
    }

    public RateLimitStrategy get(RateLimitAlgorithm algorithm) {
        if (algorithm == null) {
            algorithm = RateLimitAlgorithm.FIXED_WINDOW;
        }
        return strategies.getOrDefault(algorithm, strategies.get(RateLimitAlgorithm.FIXED_WINDOW));
    }
}
