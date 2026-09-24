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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRateLimitStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private SlidingWindowRateLimitStrategy strategy;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        strategy = new SlidingWindowRateLimitStrategy(redisTemplate);
        // Fixed time: 2024-01-15 10:00:30 UTC
        fixedClock = Clock.fixed(
                Instant.ofEpochSecond(1705310430L),
                ZoneOffset.UTC
        );
    }

    @Test
    void check_requestUnderLimit_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 9L, 60L));

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(9);
        assertThat(result.resetSeconds()).isEqualTo(60);
    }

    @Test
    void check_requestAtLimit_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 0L, 60L));

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void check_requestOverLimit_rejected() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(0L, 0L, 42L));

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(0);
        assertThat(result.resetSeconds()).isEqualTo(42);
    }

    @Test
    void check_resetTime_whenWindowEmpty_usesWindowDuration() {
        // Lua decides this; strategy just maps return values.
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 9L, 60L));

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertThat(result.allowed()).isTrue();
        assertThat(result.resetSeconds()).isEqualTo(60);
    }

    @Test
    void check_uniqueZsetMembers_areUsedAcrossRequests() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 9L, 60L));

        strategy.check("key1", 10, 60, fixedClock);
        strategy.check("key1", 10, 60, fixedClock);
        strategy.check("key1", 10, 60, fixedClock);

        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);

        // execute(script, [key], now, windowSeconds, requestLimit, memberId)
        verify(redisTemplate, times(3))
                .execute(any(RedisScript.class), eq(List.of("rate_limit:sliding:key1")),
                        anyString(), eq("60"), eq("10"), memberCaptor.capture());

        Set<String> uniqueMembers = new HashSet<>(memberCaptor.getAllValues());
        assertThat(uniqueMembers).hasSize(3);
    }
}
