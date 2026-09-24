package com.apishield.integration;

import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;

import static org.assertj.core.api.Assertions.assertThat;

import static org.hamcrest.Matchers.*;
import static io.restassured.RestAssured.given;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimitE2ETest extends BaseIntegrationTest {

    @Test
    @org.junit.jupiter.api.Order(1)
    void fixedWindow_underLimit_thenOverLimit_uses429HeadersAndBody() {
        int requestLimit = 5;
        int windowSeconds = 10;

        String apiKey = given()
                .contentType(ContentType.JSON)
                .body(new Object() {
                    public final String name = "e2e-fixed";
                    public final int requestLimit = 5;
                    public final int windowSeconds = 10;
                    public final String algorithm = "FIXED_WINDOW";
                })
                .when()
                .post("/api/clients")
                .then()
                .statusCode(201)
                .body("apiKey", startsWith("ask_live_"))
                .extract()
                .path("apiKey");

        // Align to the start of a fixed window to avoid minute/second boundary flakiness.
        waitForFixedWindowStart(windowSeconds);

        // 1..5 should be allowed (5th request should leave remaining=0 but still be allowed)
        for (int i = 1; i <= requestLimit; i++) {
            int remaining = requestLimit - i;
            given()
                    .header("X-API-Key", apiKey)
                    .when()
                    .get("/api/demo/products")
                    .then()
                    .statusCode(200)
                    .header("X-RateLimit-Limit", String.valueOf(requestLimit))
                    .header("X-RateLimit-Remaining", String.valueOf(remaining))
                    .header("X-RateLimit-Reset", notNullValue());
        }

        // 6th request should be rejected
        Response rejected = given()
                .header("X-API-Key", apiKey)
                .when()
                .get("/api/demo/products")
                .then()
                .statusCode(429)
                .header("X-RateLimit-Limit", String.valueOf(requestLimit))
                .header("X-RateLimit-Remaining", "0")
                .header("X-RateLimit-Reset", notNullValue())
                .header("Retry-After", notNullValue())
                .body("status", is(429))
                .body("error", is("Too Many Requests"))
                .body("message", is("Rate limit exceeded"))
                .extract()
                .response();

        String reset = rejected.getHeader("X-RateLimit-Reset");
        assertThat(rejected.getHeader("Retry-After"))
                .as("Retry-After should match X-RateLimit-Reset")
                .isEqualTo(reset);
    }

    @Test
    @org.junit.jupiter.api.Order(2)
    void whenRedisIsDown_rateLimiterFailsClosed_returns503() {
        String apiKey = given()
                .contentType(ContentType.JSON)
                .body(new Object() {
                    public final String name = "e2e-redis-down";
                    public final int requestLimit = 2;
                    public final int windowSeconds = 60;
                    public final String algorithm = "FIXED_WINDOW";
                })
                .when()
                .post("/api/clients")
                .then()
                .statusCode(201)
                .extract()
                .path("apiKey");

        redis.stop();

        // Wait for the server to fully stop (prevents flaky container shutdown failures)
        try {
            Thread.sleep(500);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        try {
            Response resp = null;
            int attempts = 0;
            while (attempts < 5) {
                attempts++;
                resp = given()
                        .header("X-API-Key", apiKey)
                        .when()
                        .get("/api/demo/products")
                        .then()
                        .extract()
                        .response();

                if (resp.statusCode() == 503) {
                    break;
                }

                if (attempts < 5) {
                    try {
                        Thread.sleep(200);
                    } catch (InterruptedException ignored) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            assertThat(resp)
                    .as("expected a response even if redis stop propagation delays the first failure")
                    .isNotNull();
            assertThat(resp.statusCode()).isEqualTo(503);
            assertThat(resp.jsonPath().getInt("status")).isEqualTo(503);
            assertThat(resp.jsonPath().getString("error")).isEqualTo("Service Unavailable");
            assertThat(resp.jsonPath().getString("message")).isEqualTo("Rate limiting service is temporarily unavailable");

            assertThat(resp.getHeader("X-RateLimit-Limit")).isNull();
        } finally {
            // Intentionally do not restart Redis here.
            // This test is designed to validate fail-closed behavior; restarting can be flaky.
        }
    }

    /**
     * Sleeps until just after the next window boundary so sequential requests
     * cannot straddle two windows.
     */
    private static void waitForFixedWindowStart(int windowSeconds) {
        long now = System.currentTimeMillis() / 1000;
        long remainder = now % windowSeconds;
        if (remainder == 0) {
            return;
        }
        long sleepMs = (windowSeconds - remainder) * 1000L + 250L;
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
