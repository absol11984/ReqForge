package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.RateLimitAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(redisTemplate);

        // Strategies call opsFor* lazily inside check(), so stubbing these once is fine.
        // Use lenient() to avoid UnnecessaryStubbing when a test only exercises one strategy.
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        @SuppressWarnings({"unchecked", "rawtypes"})
        HashOperations rawOps = (HashOperations) hashOperations;
        lenient().when((HashOperations) redisTemplate.opsForHash()).thenReturn(rawOps);
    }

    @Test
    void checkRateLimit_firstRequest_allowed_fixedWindow() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.resetSeconds()).isBetween(1L, 60L);
        verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(60)));
    }

    @Test
    void checkRateLimit_overLimit_rejected_fixedWindow() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void checkRateLimit_routesToSlidingWindow_strategy() {
        when(zSetOperations.rangeByScore(anyString(), eq(Double.NEGATIVE_INFINITY), anyDouble()))
                .thenReturn(Collections.emptySet());
        when(zSetOperations.size(anyString())).thenReturn(0L);
        when(zSetOperations.add(anyString(), anyString(), anyDouble())).thenReturn(true);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        RateLimitResult result = rateLimitService.checkRateLimit(
                "ask_live_sliding",
                5,
                60,
                RateLimitAlgorithm.SLIDING_WINDOW
        );

        assertThat(result.allowed()).isTrue();
        verify(redisTemplate).opsForZSet();
        verify(redisTemplate, never()).opsForValue();
    }

    @Test
    void checkRateLimit_routesToTokenBucket_strategy() {
        // new bucket: state is missing => get() returns null
        when(hashOperations.get(anyString(), eq("tokens"))).thenReturn(null);
        when(hashOperations.get(anyString(), eq("lastRefillSeconds"))).thenReturn(null);

        RateLimitResult result = rateLimitService.checkRateLimit(
                "ask_live_bucket",
                5,
                60,
                RateLimitAlgorithm.TOKEN_BUCKET
        );

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.resetSeconds()).isEqualTo(0);
        verify(redisTemplate).opsForHash();
        verify(redisTemplate, never()).opsForValue();
        verify(redisTemplate, never()).opsForZSet();
    }

    @Test
    void checkRateLimit_usesCorrectKeyPattern_fixedWindow() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        rateLimitService.checkRateLimit("ask_live_abc", 5, 60);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        String key = keyCaptor.getValue();
        long windowId = Instant.now().getEpochSecond() / 60;
        assertThat(key).isEqualTo("rate_limit:fixed:ask_live_abc:" + windowId);
    }

    @Test
    void checkRateLimit_correctResetSeconds_fixedWindow() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        long before = Instant.now().getEpochSecond();
        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        long expectedReset = ((before / 60) + 1) * 60 - before;
        assertThat(result.resetSeconds()).isBetween(Math.max(1, expectedReset - 1), expectedReset + 1);
    }
}
