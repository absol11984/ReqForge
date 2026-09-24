package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.RateLimitAlgorithm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.Objects;

@Service
public class RateLimitService {

    private final Clock clock;
    private final RateLimitStrategyFactory strategyFactory;

    public RateLimitService(StringRedisTemplate redisTemplate) {
        this(Clock.systemUTC(), new RateLimitStrategyFactory(redisTemplate));
    }

    @Autowired
    public RateLimitService(Clock clock, RateLimitStrategyFactory strategyFactory) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.strategyFactory = Objects.requireNonNull(strategyFactory, "strategyFactory");
    }

    public RateLimitResult checkRateLimit(String apiKey, int requestLimit, int windowSeconds) {
        return checkRateLimit(apiKey, requestLimit, windowSeconds, RateLimitAlgorithm.FIXED_WINDOW);
    }

    public RateLimitResult checkRateLimit(String apiKey, int requestLimit, int windowSeconds,
                                          RateLimitAlgorithm algorithm) {
        RateLimitStrategy strategy = strategyFactory.get(algorithm);
        return strategy.check(apiKey, requestLimit, windowSeconds, clock);
    }
}
