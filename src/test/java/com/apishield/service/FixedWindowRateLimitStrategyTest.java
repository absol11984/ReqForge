package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FixedWindowRateLimitStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private FixedWindowRateLimitStrategy strategy;
    private Clock fixedClock;

    @BeforeEach
    void setUp() {
        strategy = new FixedWindowRateLimitStrategy(redisTemplate);
        // Fixed time: 2024-01-15 10:00:30 UTC
        fixedClock = Clock.fixed(
                Instant.ofEpochSecond(1705310430L),
                ZoneOffset.UTC
        );
    }

    @Test
    void check_requestUnderLimit_allowed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertTrue(result.allowed());
        assertEquals(10, result.limit());
        assertEquals(9, result.remaining());
        assertTrue(result.resetSeconds() > 0);
    }

    @Test
    void check_requestAtLimit_allowed() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(10L);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertTrue(result.allowed());
        assertEquals(10, result.limit());
        assertEquals(0, result.remaining());
    }

    @Test
    void check_requestOverLimit_rejected() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(11L);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertFalse(result.allowed());
        assertEquals(10, result.limit());
        assertEquals(0, result.remaining());
    }

    @Test
    void check_newKey_startsAtOne() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        RateLimitResult result = strategy.check("newkey", 10, 60, fixedClock);

        assertTrue(result.allowed());
        assertEquals(9, result.remaining());
        verify(valueOperations).increment(contains("rate_limit:fixed:newkey"));
    }

    @Test
    void check_differentWindows_haveDifferentKeys() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // Window 1: epoch 1705310400 (10:00:00 UTC)
        strategy.check("key1", 10, 60, fixedClock);

        // Window 2: epoch 1705310460 (10:01:00 UTC)
        Clock laterClock = Clock.fixed(
                Instant.ofEpochSecond(1705310460L),
                ZoneOffset.UTC
        );
        strategy.check("key1", 10, 60, laterClock);

        verify(redisTemplate, times(2)).opsForValue();
        verify(valueOperations, times(2)).increment(anyString());
    }
}