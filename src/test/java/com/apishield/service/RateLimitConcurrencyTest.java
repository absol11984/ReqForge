package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RateLimitConcurrencyTest {

    private static final Clock FIXED_CLOCK = Clock.fixed(
            Instant.ofEpochSecond(1_705_310_430L),
            ZoneOffset.UTC
    );

    @Test
    void fixedWindow_concurrentRequests_doNotExceedLimit() throws Exception {
        int limit = 10;
        int windowSeconds = 60;
        int requests = 50;
        String apiKey = "ask_live_fixed_conc";

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        Map<String, Long> countsByKey = new HashMap<>();

        // Atomic INCR+optional TTL behavior emulation
        when(redisTemplate.execute(any(RedisScript.class), anyList(), anyString()))
                .thenAnswer(invocation -> {
                    Object[] args = invocation.getArguments();
                    @SuppressWarnings("unchecked")
                    List<String> keys = (List<String>) args[1];
                    String key = keys.get(0);

                    synchronized (countsByKey) {
                        long current = countsByKey.getOrDefault(key, 0L);
                        current++;
                        countsByKey.put(key, current);
                        return current;
                    }
                });

        FixedWindowRateLimitStrategy strategy = new FixedWindowRateLimitStrategy(redisTemplate);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requests);

        try {
            Future<Boolean>[] futures = new Future[requests];
            for (int i = 0; i < requests; i++) {
                futures[i] = pool.submit(() -> {
                    startLatch.await();
                    try {
                        RateLimitResult r = strategy.check(apiKey, limit, windowSeconds, FIXED_CLOCK);
                        return r.allowed();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();

            int allowed = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) allowed++;
            }
            assertThat(allowed).isEqualTo(limit);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void slidingWindow_concurrentRequests_doNotExceedLimit() throws Exception {
        int limit = 10;
        int windowSeconds = 60;
        int requests = 50;
        String apiKey = "ask_live_sliding_conc";

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        Map<String, Long> countsByKey = new HashMap<>();

        // Atomic ZREMRANGEBYSCORE+ZCARD+conditional ZADD emulation for fixed-clock test.
        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString(),
                anyString()
        )).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) args[1];
            String key = keys.get(0);

            long now = Long.parseLong(String.valueOf(args[2]));
            long window = Long.parseLong(String.valueOf(args[3]));
            long requestLimit = Long.parseLong(String.valueOf(args[4]));
            // args[5] is memberId (ignored for this test emulation)

            synchronized (countsByKey) {
                long currentCount = countsByKey.getOrDefault(key, 0L);
                boolean allowed = currentCount < requestLimit;
                long remaining;
                long resetSeconds;

                if (allowed) {
                    currentCount++;
                    countsByKey.put(key, currentCount);
                    remaining = Math.max(0, requestLimit - currentCount);
                    resetSeconds = window;
                    return Arrays.asList(1L, remaining, resetSeconds);
                }

                remaining = 0;
                resetSeconds = window;
                return Arrays.asList(0L, remaining, resetSeconds);
            }
        });

        SlidingWindowRateLimitStrategy strategy = new SlidingWindowRateLimitStrategy(redisTemplate);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requests);

        try {
            Future<Boolean>[] futures = new Future[requests];
            for (int i = 0; i < requests; i++) {
                futures[i] = pool.submit(() -> {
                    startLatch.await();
                    try {
                        RateLimitResult r = strategy.check(apiKey, limit, windowSeconds, FIXED_CLOCK);
                        return r.allowed();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();

            int allowed = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) allowed++;
            }
            assertThat(allowed).isEqualTo(limit);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void tokenBucket_concurrentRequests_doNotExceedLimit() throws Exception {
        int limit = 10;
        int windowSeconds = 60;
        int requests = 50;
        String apiKey = "ask_live_bucket_conc";

        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);

        Map<String, Double> tokensByKey = new HashMap<>();
        Map<String, Long> lastRefillByKey = new HashMap<>();

        when(redisTemplate.execute(
                any(RedisScript.class),
                anyList(),
                anyString(),
                anyString(),
                anyString()
        )).thenAnswer(invocation -> {
            Object[] args = invocation.getArguments();
            @SuppressWarnings("unchecked")
            List<String> keys = (List<String>) args[1];
            String key = keys.get(0);

            long requestLimit = Long.parseLong(String.valueOf(args[2]));
            long window = Long.parseLong(String.valueOf(args[3]));
            long now = Long.parseLong(String.valueOf(args[4]));

            double refillRate = requestLimit / (double) window;

            synchronized (tokensByKey) {
                Double tokens = tokensByKey.get(key);
                Long lastRefill = lastRefillByKey.get(key);

                if (tokens == null || lastRefill == null) {
                    tokens = (double) requestLimit;
                    lastRefill = now;
                }

                long elapsed = Math.max(0, now - lastRefill);
                tokens = Math.min(requestLimit, tokens + elapsed * refillRate);

                boolean allowed = tokens >= 1.0;
                if (allowed) {
                    tokens -= 1.0;
                }

                lastRefillByKey.put(key, now);
                tokensByKey.put(key, tokens);

                long remaining = (long) Math.floor(tokens);
                long resetSeconds = allowed ? 0L : 1L;
                return Arrays.asList(allowed ? 1L : 0L, remaining, resetSeconds);
            }
        });

        TokenBucketRateLimitStrategy strategy = new TokenBucketRateLimitStrategy(redisTemplate);

        ExecutorService pool = Executors.newFixedThreadPool(16);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(requests);

        try {
            Future<Boolean>[] futures = new Future[requests];
            for (int i = 0; i < requests; i++) {
                futures[i] = pool.submit(() -> {
                    startLatch.await();
                    try {
                        RateLimitResult r = strategy.check(apiKey, limit, windowSeconds, FIXED_CLOCK);
                        return r.allowed();
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            startLatch.countDown();
            doneLatch.await();

            int allowed = 0;
            for (Future<Boolean> f : futures) {
                if (f.get()) allowed++;
            }
            assertThat(allowed).isEqualTo(limit);
        } finally {
            pool.shutdownNow();
        }
    }
}
