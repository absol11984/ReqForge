package com.apishield.service;

import com.apishield.entity.RateLimitAlgorithm;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class RateLimitStrategyFactoryTest {

    @Test
    void factory_returnsFixedWindowStrategy() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitStrategyFactory factory = new RateLimitStrategyFactory(redisTemplate);

        RateLimitStrategy strategy = factory.get(RateLimitAlgorithm.FIXED_WINDOW);

        assertThat(strategy).isInstanceOf(FixedWindowRateLimitStrategy.class);
    }

    @Test
    void factory_returnsSlidingWindowStrategy() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitStrategyFactory factory = new RateLimitStrategyFactory(redisTemplate);

        RateLimitStrategy strategy = factory.get(RateLimitAlgorithm.SLIDING_WINDOW);

        assertThat(strategy).isInstanceOf(SlidingWindowRateLimitStrategy.class);
    }

    @Test
    void factory_returnsTokenBucketStrategy() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitStrategyFactory factory = new RateLimitStrategyFactory(redisTemplate);

        RateLimitStrategy strategy = factory.get(RateLimitAlgorithm.TOKEN_BUCKET);

        assertThat(strategy).isInstanceOf(TokenBucketRateLimitStrategy.class);
    }

    @Test
    void nullAlgorithm_defaultsToFixedWindow() {
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        RateLimitStrategyFactory factory = new RateLimitStrategyFactory(redisTemplate);

        RateLimitStrategy strategy = factory.get(null);

        assertThat(strategy).isInstanceOf(FixedWindowRateLimitStrategy.class);
    }
}
