package com.apishield.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "clients")
public class Client {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "api_key", nullable = false, unique = true, length = 128)
    private String apiKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 32)
    private ClientStatus status = ClientStatus.ACTIVE;

    @Enumerated(EnumType.STRING)
    @Column(name = "rate_limit_algorithm", nullable = false, length = 32)
    private RateLimitAlgorithm algorithm = RateLimitAlgorithm.FIXED_WINDOW;

    /** Maximum requests allowed within a single window. */
    @Column(name = "request_limit", nullable = false)
    private int requestLimit;

    /** Length of each rate-limit window, in seconds. */
    @Column(name = "window_seconds", nullable = false)
    private int windowSeconds;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Client() {
        // JPA
    }

    public Client(String name, String apiKey, ClientStatus status) {
        this(UUID.randomUUID(), name, apiKey, status, 100, 60);
    }

    public Client(UUID id, String name, String apiKey, ClientStatus status,
                  int requestLimit, int windowSeconds) {
        this(id, name, apiKey, status, requestLimit, windowSeconds, RateLimitAlgorithm.FIXED_WINDOW);
    }

    public Client(UUID id, String name, String apiKey, ClientStatus status,
                  int requestLimit, int windowSeconds, RateLimitAlgorithm algorithm) {
        if (id == null) {
            throw new IllegalArgumentException("id is required");
        }
        this.id = id;
        this.name = name;
        this.apiKey = apiKey;
        this.status = status == null ? ClientStatus.ACTIVE : status;
        this.algorithm = algorithm == null ? RateLimitAlgorithm.FIXED_WINDOW : algorithm;
        this.requestLimit = requestLimit;
        this.windowSeconds = windowSeconds;
    }

    public Client(UUID id, String name, String apiKey, ClientStatus status) {
        this(id, name, apiKey, status, 100, 60);
    }

    @PrePersist
    void onPrePersist() {
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (algorithm == null) algorithm = RateLimitAlgorithm.FIXED_WINDOW;
        if (id == null) id = UUID.randomUUID();
    }

    @PreUpdate
    void onPreUpdate() {
        updatedAt = Instant.now();
        if (algorithm == null) algorithm = RateLimitAlgorithm.FIXED_WINDOW;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getApiKey() { return apiKey; }
    public ClientStatus getStatus() { return status; }
    public RateLimitAlgorithm getAlgorithm() {
        return algorithm == null ? RateLimitAlgorithm.FIXED_WINDOW : algorithm;
    }
    public int getRequestLimit() { return requestLimit; }
    public int getWindowSeconds() { return windowSeconds; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setName(String name) { this.name = name; }
    public void setStatus(ClientStatus status) { this.status = status; }
    public void setAlgorithm(RateLimitAlgorithm algorithm) { this.algorithm = algorithm; }
    public void setRequestLimit(int requestLimit) { this.requestLimit = requestLimit; }
    public void setWindowSeconds(int windowSeconds) { this.windowSeconds = windowSeconds; }
}
