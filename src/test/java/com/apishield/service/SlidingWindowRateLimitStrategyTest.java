package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SlidingWindowRateLimitStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

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
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        // Window is empty, count = 0, space for 10
        when(zSetOperations.size(anyString())).thenReturn(0L);
        // Request allowed, add timestamp
        when(zSetOperations.add(eq("rate_limit:sliding:key1"), anyString(), anyDouble()))
                .thenReturn(true);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertTrue(result.allowed());
        assertEquals(10, result.limit());
        assertEquals(9, result.remaining());
    }

    @Test
    void check_requestAtLimit_allowed() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        // 9 existing requests, adding 1 brings to limit
        when(zSetOperations.size(anyString())).thenReturn(9L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertTrue(result.allowed());
        assertEquals(10, result.limit());
        assertEquals(0, result.remaining());
    }

    @Test
    void check_requestOverLimit_rejected() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        // Already at limit
        when(zSetOperations.size(anyString())).thenReturn(10L);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertFalse(result.allowed());
        assertEquals(10, result.limit());
        assertEquals(0, result.remaining());
        // Should NOT have added a new entry
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void check_expiredRequests_removedBeforeCounting() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        // Some old requests to expire
        Set<String> oldRequests = new LinkedHashSet<>();
        oldRequests.add("entry1");
        oldRequests.add("entry2");
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(oldRequests);

        // After removing 2 old entries, only 1 remains (limit is 10)
        when(zSetOperations.size(anyString())).thenReturn(1L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertTrue(result.allowed());
        assertEquals(8, result.remaining());
        verify(zSetOperations).remove(eq("rate_limit:sliding:key1"), eq(oldRequests));
    }

    @Test
    void check_remainingCalculation_correctAfterMultipleRequests() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        // Simulate 3 requests already in window
        when(zSetOperations.size(anyString()))
                .thenReturn(0L)   // First call (before add): count = 0, add, now 1
                .thenReturn(1L)   // Second request: count = 1, add, now 2
                .thenReturn(2L);  // Third request: count = 2, add, now 3

        // Request 1
        RateLimitResult r1 = strategy.check("key1", 10, 60, fixedClock);
        assertEquals(9, r1.remaining());

        // Request 2
        RateLimitResult r2 = strategy.check("key1", 10, 60, fixedClock);
        assertEquals(8, r2.remaining());

        // Request 3
        RateLimitResult r3 = strategy.check("key1", 10, 60, fixedClock);
        assertEquals(7, r3.remaining());
    }

    @Test
    void check_resetTime_calculatedFromOldestEntry() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        when(zSetOperations.size(anyString())).thenReturn(1L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        // Oldest entry was added 10 seconds ago (at 1705310420)
        Set<String> oldest = new LinkedHashSet<>();
        oldest.add("oldestEntry");
        when(zSetOperations.range("rate_limit:sliding:key1", 0, 0)).thenReturn(oldest);
        when(zSetOperations.score("rate_limit:sliding:key1", "oldestEntry")).thenReturn(1705310420.0);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertTrue(result.allowed());
        // Oldest expires in: 1705310420 + 60 - 1705310430 = 50 seconds
        assertEquals(50, result.resetSeconds());
    }

    @Test
    void check_resetTime_whenWindowEmpty_usesWindowDuration() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        // Empty window
        when(zSetOperations.size(anyString())).thenReturn(0L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertTrue(result.allowed());
        // When window is empty, reset time is the full window duration
        assertEquals(60, result.resetSeconds());
    }

    @Test
    void check_uniqueZsetMembers_areUsedAcrossRequests() {
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);

        // Allow three requests
        when(zSetOperations.size(anyString()))
                .thenReturn(0L)
                .thenReturn(1L)
                .thenReturn(2L);

        strategy.check("key1", 10, 60, fixedClock);
        strategy.check("key1", 10, 60, fixedClock);
        strategy.check("key1", 10, 60, fixedClock);

        ArgumentCaptor<String> memberCaptor = ArgumentCaptor.forClass(String.class);
        verify(zSetOperations, times(3))
                .add(eq("rate_limit:sliding:key1"), memberCaptor.capture(), anyDouble());

        assertEquals(3, memberCaptor.getAllValues().size());
        assertEquals(3, memberCaptor.getAllValues().stream().distinct().count());
    }
}
