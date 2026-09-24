package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TokenBucketRateLimitStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private TokenBucketRateLimitStrategy strategy;
    private Clock clock;

    @BeforeEach
    void setUp() {
        strategy = new TokenBucketRateLimitStrategy(redisTemplate);
        clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
    }

    @Test
    void newBucket_startsWithFullCapacity() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 4L, 0L));

        RateLimitResult result = strategy.check("apiKey1", 5, 60, clock);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.resetSeconds()).isEqualTo(0);
    }

    @Test
    void oneRequest_consumesOneToken() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 4L, 0L))
                .thenReturn(List.of(1L, 3L, 0L));

        strategy.check("apiKey1", 5, 60, clock);
        RateLimitResult second = strategy.check("apiKey1", 5, 60, clock);

        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isEqualTo(3);
    }

    @Test
    void multipleRequests_rejectWhenEmpty() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(
                        List.of(1L, 2L, 0L),
                        List.of(1L, 1L, 0L),
                        List.of(1L, 0L, 0L),
                        List.of(0L, 0L, 5L)
                );

        RateLimitResult r1 = strategy.check("apiKey1", 3, 60, clock);
        RateLimitResult r2 = strategy.check("apiKey1", 3, 60, clock);
        RateLimitResult r3 = strategy.check("apiKey1", 3, 60, clock);
        RateLimitResult r4 = strategy.check("apiKey1", 3, 60, clock);

        assertThat(r1.allowed()).isTrue();
        assertThat(r2.allowed()).isTrue();
        assertThat(r3.allowed()).isTrue();
        assertThat(r4.allowed()).isFalse();
        assertThat(r4.remaining()).isEqualTo(0);
        assertThat(r4.resetSeconds()).isEqualTo(5);
    }

    @Test
    void arguments_includeNowAndCapacity() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 4L, 0L));

        strategy.check("apiKey1", 5, 60, clock);

        ArgumentCaptor<String> nowCaptor = ArgumentCaptor.forClass(String.class);
        // execute(script, [key], requestLimit, windowSeconds, now)
        verify(redisTemplate).execute(any(RedisScript.class), eq(List.of("rate_limit:bucket:apiKey1")),
                eq("5"), eq("60"), nowCaptor.capture());

        assertThat(nowCaptor.getValue()).isEqualTo(String.valueOf(1_700_000_000L));
    }
}
