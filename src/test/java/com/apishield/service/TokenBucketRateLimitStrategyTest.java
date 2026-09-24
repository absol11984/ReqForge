package com.apishield.service;

import com.apishield.dto.RateLimitResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TokenBucketRateLimitStrategyTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, String, String> hashOperations;

    private TokenBucketRateLimitStrategy strategy;

    // In-memory state to emulate Redis hash
    private final Map<String, Map<String, String>> hashState = new HashMap<>();

    private static final String BUCKET_KEY = "rate_limit:bucket:apiKey1";

    private Clock clock;

    @BeforeEach
    void setUp() {
        strategy = new TokenBucketRateLimitStrategy(redisTemplate);

        // RedisTemplate.opsForHash() is generic; cast to what TokenBucketRateLimitStrategy expects.
        @SuppressWarnings({"unchecked", "rawtypes"})
        HashOperations rawOps = (HashOperations) hashOperations;
        when(((StringRedisTemplate) redisTemplate).opsForHash()).thenReturn(rawOps);

        // Default: hash get returns null when field not present
        when(hashOperations.get(eq(BUCKET_KEY), anyString()))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(0, String.class);
                    String field = invocation.getArgument(1, String.class);
                    Map<String, String> fields = hashState.get(key);
                    return fields == null ? null : fields.get(field);
                });

        // HashOperations.put returns void; use doAnswer.
        doAnswer(invocation -> {
            String key = invocation.getArgument(0, String.class);
            String field = invocation.getArgument(1, String.class);
            String value = invocation.getArgument(2, String.class);
            hashState.computeIfAbsent(key, k -> new HashMap<>()).put(field, value);
            return null;
        }).when(hashOperations).put(eq(BUCKET_KEY), anyString(), anyString());

        // Fixed start time
        clock = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L), ZoneOffset.UTC);
    }

    @Test
    void newBucket_startsWithFullCapacity() {
        RateLimitResult result = strategy.check("apiKey1", 5, 60, clock);

        assertThat(result.allowed()).isTrue();
        assertThat(result.limit()).isEqualTo(5);
        assertThat(result.remaining()).isEqualTo(4);
        assertThat(result.resetSeconds()).isEqualTo(0);
    }

    @Test
    void oneRequest_consumesOneToken() {
        strategy.check("apiKey1", 5, 60, clock);
        RateLimitResult second = strategy.check("apiKey1", 5, 60, clock);

        assertThat(second.allowed()).isTrue();
        assertThat(second.remaining()).isEqualTo(3);
    }

    @Test
    void multipleRequests_consumeMultipleTokens_andRejectWhenEmpty() {
        // capacity=3
        RateLimitResult r1 = strategy.check("apiKey1", 3, 60, clock);
        RateLimitResult r2 = strategy.check("apiKey1", 3, 60, clock);
        RateLimitResult r3 = strategy.check("apiKey1", 3, 60, clock);
        RateLimitResult r4 = strategy.check("apiKey1", 3, 60, clock);

        assertThat(r1.allowed()).isTrue();
        assertThat(r1.remaining()).isEqualTo(2);
        assertThat(r2.allowed()).isTrue();
        assertThat(r2.remaining()).isEqualTo(1);
        assertThat(r3.allowed()).isTrue();
        assertThat(r3.remaining()).isEqualTo(0);

        assertThat(r4.allowed()).isFalse();
        assertThat(r4.remaining()).isEqualTo(0);
        assertThat(r4.resetSeconds()).isGreaterThanOrEqualTo(1);
    }

    @Test
    void refill_tokens_overTime_andNeverExceedsCapacity() {
        // capacity=2
        strategy.check("apiKey1", 2, 60, clock); // remaining 1
        strategy.check("apiKey1", 2, 60, clock); // remaining 0

        // After 10 seconds, refillRate = 2/60 tokens per second. elapsed=10 => 0.333 tokens.
        // available should stay at 0.x but capped later; strategy allows only when >=1.
        Clock later = Clock.fixed(Instant.ofEpochSecond(1_700_000_010L), ZoneOffset.UTC);
        RateLimitResult stillRejected = strategy.check("apiKey1", 2, 60, later);
        assertThat(stillRejected.allowed()).isFalse();

        // After enough time to refill at least 1 token.
        // missingTokens for 1 token is (1 - available). With available about 0.333 -> missing ~0.667.
        // refillRate ~0.03333 => time ~20s.
        Clock laterEnough = Clock.fixed(Instant.ofEpochSecond(1_700_000_030L), ZoneOffset.UTC);
        RateLimitResult allowed = strategy.check("apiKey1", 2, 60, laterEnough);
        assertThat(allowed.allowed()).isTrue();
        assertThat(allowed.remaining()).isLessThanOrEqualTo(1);

        // If we wait again long enough, it should cap at capacity.
        // At farFuture the bucket refills to at most 1 token (then we immediately consume it).
        Clock farFuture = Clock.fixed(Instant.ofEpochSecond(1_700_000_000L + 60), ZoneOffset.UTC);
        RateLimitResult allowedAfterCap = strategy.check("apiKey1", 2, 60, farFuture);
        assertThat(allowedAfterCap.allowed()).isTrue();
        assertThat(allowedAfterCap.limit()).isEqualTo(2);
        assertThat(allowedAfterCap.remaining()).isEqualTo(0);
    }

    @Test
    void remainingTokens_reportedCorrectlyOnRejection() {
        strategy.check("apiKey1", 2, 60, clock); // remaining 1
        RateLimitResult rejected = strategy.check("apiKey1", 2, 60, clock); // consume last -> remaining 0, allowed true
        assertThat(rejected.allowed()).isTrue();
        assertThat(rejected.remaining()).isEqualTo(0);

        RateLimitResult rejectedNow = strategy.check("apiKey1", 2, 60, clock); // empty
        assertThat(rejectedNow.allowed()).isFalse();
        assertThat(rejectedNow.remaining()).isEqualTo(0);
        assertThat(rejectedNow.resetSeconds()).isGreaterThan(0);
    }
}
