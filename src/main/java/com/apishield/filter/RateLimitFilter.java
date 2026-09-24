package com.apishield.filter;

import com.apishield.dto.RateLimitResult;
import com.apishield.entity.Client;
import com.apishield.entity.ClientStatus;
import com.apishield.entity.RateLimitAlgorithm;
import com.apishield.exception.ErrorResponse;
import com.apishield.service.ClientService;
import com.apishield.service.RateLimitService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;

@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final ClientService clientService;
    private final RateLimitService rateLimitService;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ClientService clientService,
                           RateLimitService rateLimitService,
                           ObjectMapper objectMapper) {
        this.clientService = clientService;
        this.rateLimitService = rateLimitService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/api/clients")
                || path.startsWith("/swagger-ui")
                || path.startsWith("/v3/api-docs")
                || path.equals("/favicon.ico");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String apiKey = request.getHeader("X-API-Key");
        if (apiKey == null || apiKey.isBlank()) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Missing X-API-Key header", request.getRequestURI());
            return;
        }

        Client client = clientService.getClientEntityByApiKey(apiKey);
        if (client == null) {
            writeError(response, HttpStatus.UNAUTHORIZED, "Unauthorized",
                    "Invalid or unknown API key", request.getRequestURI());
            return;
        }
        if (client.getStatus() != ClientStatus.ACTIVE) {
            writeError(response, HttpStatus.FORBIDDEN, "Forbidden",
                    "Client is inactive", request.getRequestURI());
            return;
        }

        RateLimitResult result = rateLimitService.checkRateLimit(
                apiKey,
                client.getRequestLimit(),
                client.getWindowSeconds(),
                client.getAlgorithm()
        );
        if (!result.allowed()) {
            response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
            response.setHeader("X-RateLimit-Remaining", "0");
            response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
            response.setHeader("Retry-After", String.valueOf(result.resetSeconds()));
            writeError(response, HttpStatus.TOO_MANY_REQUESTS, "Too Many Requests",
                    "Rate limit exceeded", request.getRequestURI());
            return;
        }

        response.setHeader("X-RateLimit-Limit", String.valueOf(result.limit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(result.remaining()));
        response.setHeader("X-RateLimit-Reset", String.valueOf(result.resetSeconds()));
        filterChain.doFilter(request, response);
    }

    private void writeError(HttpServletResponse response, HttpStatus status,
                            String error, String message, String path) throws IOException {
        ErrorResponse body = new ErrorResponse(
                Instant.now(), status.value(), error, message, path);
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(), body);
    }
}
