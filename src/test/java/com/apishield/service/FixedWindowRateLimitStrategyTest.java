package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
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
class FixedWindowRateLimitStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

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
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(9);
        assertThat(result.resetSeconds()).isGreaterThan(0);
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void check_requestAtLimit_allowed() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(10L);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void check_requestOverLimit_rejected() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(11L);

        RateLimitResult result = strategy.check("key1", 10, 60, fixedClock);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(10);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void check_newKey_startsAtOne() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        strategy.check("newkey", 10, 60, fixedClock);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), anyString());

        long windowId = 1705310430L / 60;
        assertThat(keysCaptor.getValue()).containsExactly("rate_limit:fixed:newkey:" + windowId);
    }

    @Test
    void check_differentWindows_haveDifferentKeys() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        // Window 1: epoch 1705310400 (10:00:00 UTC)
        strategy.check("key1", 10, 60, fixedClock);

        // Window 2: epoch 1705310460 (10:01:00 UTC)
        Clock laterClock = Clock.fixed(
                Instant.ofEpochSecond(1705310460L),
                ZoneOffset.UTC
        );
        strategy.check("key1", 10, 60, laterClock);

        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate, times(2)).execute(any(RedisScript.class), keysCaptor.capture(), anyString());

        long windowId1 = 1705310430L / 60;
        long windowId2 = 1705310460L / 60;

        assertThat(keysCaptor.getAllValues())
                .containsExactly(
                        List.of("rate_limit:fixed:key1:" + windowId1),
                        List.of("rate_limit:fixed:key1:" + windowId2)
                );
    }
}
