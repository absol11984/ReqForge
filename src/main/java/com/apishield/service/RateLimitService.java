package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.RateLimitAlgorithm;
import com.apishield.exception.RateLimiterUnavailableException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
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
        try {
            return strategy.check(apiKey, requestLimit, windowSeconds, clock);
        } catch (RateLimiterUnavailableException ex) {
            throw ex;
        } catch (DataAccessException ex) {
            // Fail-closed: the filter layer will map this to HTTP 503.
            throw new RateLimiterUnavailableException("Rate limiter unavailable", ex);
        } catch (RuntimeException ex) {
            // Defensive: treat any unexpected Redis-related runtime issues as unavailable.
            throw new RateLimiterUnavailableException("Rate limiter unavailable", ex);
        }
    }
}
