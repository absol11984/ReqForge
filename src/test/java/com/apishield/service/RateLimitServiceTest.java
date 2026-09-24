package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.RateLimitAlgorithm;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void checkRateLimit_firstRequest_allowed_fixedWindow() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.resetSeconds()).isBetween(1L, 60L);
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyString());
    }

    @Test
    void checkRateLimit_overLimit_rejected_fixedWindow() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(6L);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void checkRateLimit_routesToSlidingWindow_strategy() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 4L, 33L));

        RateLimitResult result = rateLimitService.checkRateLimit(
                "ask_live_sliding",
                5,
                60,
                RateLimitAlgorithm.SLIDING_WINDOW
        );

        assertThat(result.allowed()).isTrue();
        verify(redisTemplate, times(1)).execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void checkRateLimit_routesToTokenBucket_strategy() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString(), anyString(), anyString()))
                .thenReturn(List.of(1L, 4L, 0L));

        RateLimitResult result = rateLimitService.checkRateLimit(
                "ask_live_bucket",
                5,
                60,
                RateLimitAlgorithm.TOKEN_BUCKET
        );

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.resetSeconds()).isEqualTo(0);
    }

    @Test
    void checkRateLimit_usesCorrectKeyPattern_fixedWindow() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        rateLimitService.checkRateLimit("ask_live_abc", 5, 60);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> keysCaptor = ArgumentCaptor.forClass(List.class);
        verify(redisTemplate).execute(any(RedisScript.class), keysCaptor.capture(), anyString());

        long windowId = Instant.now().getEpochSecond() / 60;
        assertThat(keysCaptor.getValue()).containsExactly("rate_limit:fixed:ask_live_abc:" + windowId);
    }

    @Test
    void checkRateLimit_correctResetSeconds_fixedWindow() {
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenReturn(1L);

        long before = Instant.now().getEpochSecond();
        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        long expectedReset = ((before / 60) + 1) * 60 - before;
        assertThat(result.resetSeconds()).isBetween(Math.max(1, expectedReset - 1), expectedReset + 1);
    }
}
