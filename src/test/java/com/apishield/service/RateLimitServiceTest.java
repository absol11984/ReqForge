package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RateLimitServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private RateLimitService rateLimitService;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimitService = new RateLimitService(redisTemplate);
    }

    @Test
    void checkRateLimit_firstRequest_allowed() {
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
    void checkRateLimit_underLimit_allowed() {
        when(valueOperations.increment(anyString())).thenReturn(3L);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(2);
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void checkRateLimit_atLimit_allowed() {
        when(valueOperations.increment(anyString())).thenReturn(5L);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void checkRateLimit_overLimit_rejected() {
        when(valueOperations.increment(anyString())).thenReturn(6L);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(0);
    }

    @Test
    void checkRateLimit_usesCorrectKeyPattern() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        rateLimitService.checkRateLimit("ask_live_abc", 5, 60);

        ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).increment(keyCaptor.capture());
        String key = keyCaptor.getValue();
        long windowId = Instant.now().getEpochSecond() / 60;
        assertThat(key).isIn(
                "rate_limit:ask_live_abc:" + windowId,
                "rate_limit:ask_live_abc:" + (windowId - 1),
                "rate_limit:ask_live_abc:" + (windowId + 1)
        );
    }

    @Test
    void checkRateLimit_correctResetSeconds() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        long before = Instant.now().getEpochSecond();
        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_test123", 5, 60);

        long expectedReset = ((before / 60) + 1) * 60 - before;
        assertThat(result.resetSeconds()).isBetween(Math.max(1, expectedReset - 1), expectedReset + 1);
    }

    @Test
    void checkRateLimit_newWindow_resetsCounter() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(true);

        RateLimitResult result = rateLimitService.checkRateLimit("ask_live_newwindow", 5, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.remaining()).isEqualTo(4);
        verify(redisTemplate).expire(anyString(), eq(Duration.ofSeconds(60)));
    }
}
