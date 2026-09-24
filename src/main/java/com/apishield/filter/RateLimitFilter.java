package com.apishield.filter;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.exception.ErrorResponse;
import com.apishield.exception.RateLimiterUnavailableException;
import com.apishield.service.ClientService;
import com.apishield.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final ClientService clientService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    private final Counter totalRequests;
    private final Counter allowedRequests;
    private final Counter rejectedRequests;
    private final Counter invalidApiKeyErrors;
    private final Counter inactiveClientErrors;
    private final Counter redisFailureErrors;

    public RateLimitFilter(ClientService clientService,
                           RateLimitService rateLimitService,
                           ObjectMapper objectMapper,
                           ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.clientService = clientService;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;

        MeterRegistry meterRegistry = meterRegistryProvider.getIfAvailable();
        if (meterRegistry == null) {
            // Unit tests may instantiate the filter without an actuator/meter registry.
            // We keep the counters functional but local.
            io.micrometer.core.instrument.simple.SimpleMeterRegistry fallback = new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            this.totalRequests = Counter.builder("ratelimit.requests.total").register(fallback);
            this.allowedRequests = Counter.builder("ratelimit.requests.allowed").register(fallback);
            this.rejectedRequests = Counter.builder("ratelimit.requests.rejected").register(fallback);
            this.invalidApiKeyErrors = Counter.builder("ratelimit.errors.invalid_key").register(fallback);
            this.inactiveClientErrors = Counter.builder("ratelimit.errors.inactive_client").register(fallback);
            this.redisFailureErrors = Counter.builder("ratelimit.errors.redis_failure").register(fallback);
            return;
        }

        this.totalRequests = Counter.builder("ratelimit.requests.total").register(meterRegistry);
        this.allowedRequests = Counter.builder("ratelimit.requests.allowed").register(meterRegistry);
        this.rejectedRequests = Counter.builder("ratelimit.requests.rejected").register(meterRegistry);
        this.invalidApiKeyErrors = Counter.builder("ratelimit.errors.invalid_key").register(meterRegistry);
        this.inactiveClientErrors = Counter.builder("ratelimit.errors.inactive_client").register(meterRegistry);
        this.redisFailureErrors = Counter.builder("ratelimit.errors.redis_failure").register(meterRegistry);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/clients")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/actuator")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        this.totalRequests.increment();

        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            invalidApiKeyErrors.increment();
            log.warn("rate-limit rejected: missing X-API-Key for path={}", request.getRequestURI());
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Missing X-API-Key header", request.getRequestURI());
            return;
        }
        String maskedApiKey = maskApiKey(apiKey);

        Client client = clientService.getClientEntityByApiKey(apiKey);
        if (client == null) {
            invalidApiKeyErrors.increment();
            log.warn("rate-limit rejected: invalid apiKey={} for path={}", maskedApiKey, request.getRequestURI());
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Invalid or unknown API key", request.getRequestURI());
            return;
        }
        if (client.getStatus() != ClientStatus.ACTIVE) {
            inactiveClientErrors.increment();
            log.warn("rate-limit rejected: inactive client for apiKey={} path={}", maskedApiKey, request.getRequestURI());
            writeError(response, HttpStatus.FORBIDDEN, "Forbidden",
                    "Client is inactive", request.getRequestURI());
            return;
        }

        RateLimitResult result;
        try {
            result = rateLimitService.checkRateLimit(
                    apiKey,
                    client.getRequestLimit(),
                    client.getWindowSeconds(),
                    client.getAlgorithm()
            );
        } catch (RateLimiterUnavailableException ex) {
            redisFailureErrors.increment();
            log.error("rate-limit unavailable for apiKey={} path={}", maskedApiKey, request.getRequestURI(), ex);
            writeServiceUnavailable(response, request.getRequestURI());
            return;
        } catch (DataAccessException ex) {
            redisFailureErrors.increment();
            log.error("redis failure during rate limiting for apiKey={} path={}", maskedApiKey, request.getRequestURI(), ex);
            writeServiceUnavailable(response, request.getRequestURI());
            return;
        }

        if (!result.allowed()) {
            rejectedRequests.increment();
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
            response.setHeader("Retry-After", String.valueOf(result.resetSeconds()));
            log.info("rate-limit exceeded apiKey={} remaining=0 resetSeconds={} path={}",
                    maskedApiKey, result.resetSeconds(), request.getRequestURI());
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    "Rate limit exceeded", request.getRequestURI());
            return;
        }

        allowedRequests.increment();
        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
        filterChain.doFilter(request, response);
    }

    private void writeServiceUnavailable(HttpServletResponse response, String path) throws IOException {
        writeError(response,
                HttpStatus.SERVICE_UNAVAILABLE,
                "Service Unavailable",
                "Rate limiting service is temporarily unavailable",
                path);
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String error, String message, String path) throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), error, message, path);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String maskApiKey(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return "<missing>";
        }
        if (apiKey.length() <= 4) {
            return "****";
        }
        return apiKey.substring(0, 4) + "***";
    }
}
