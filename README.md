# APIShield

API rate limiting service built with Java 21 and Spring Boot 3.3.2.

- **Phase 1** — Client management REST API with PostgreSQL persistence
- **Phase 2** — Redis-backed Fixed Window rate limiting
- **Phase 3** — Centralized `OncePerRequestFilter` for API key validation and rate-limit enforcement
- **Phase 4** — Multiple configurable rate limiting algorithms per client (Fixed Window, Sliding Window, Token Bucket) via the Strategy Pattern

---

## Stack

| Layer | Technology |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.3.2 |
| Persistence | Spring Data JPA + PostgreSQL |
| Rate limiting | Spring Data Redis (`StringRedisTemplate`) |
| Validation | Jakarta Bean Validation |
| API docs | springdoc-openapi 2.6.0 (Swagger UI) |
| Tests | JUnit 5 + Mockito + MockMvc + H2 |

---

## Quick start

### Prerequisites

- JDK 21
- Maven 3.9+
- PostgreSQL 14+
- Redis 6+ (Phase 2+)

### Environment variables

| Variable | Default | Description |
|---|---|---|
| `SERVER_PORT` | `8080` | HTTP port |
| `DB_URL` | — (required) | JDBC URL, e.g. `jdbc:postgresql://localhost:5432/apishield` |
| `DB_USERNAME` | — (required) | PostgreSQL username |
| `DB_PASSWORD` | — (required) | PostgreSQL password |
| `REDIS_HOST` | `localhost` | Redis hostname |
| `REDIS_PORT` | `6379` | Redis port |

### Run

```bash
export DB_URL=jdbc:postgresql://localhost:5432/apishield
export DB_USERNAME=apishield
export DB_PASSWORD=apishield
export REDIS_HOST=localhost
export REDIS_PORT=6379

mvn spring-boot:run
```

Swagger UI: http://localhost:8080/swagger-ui.html  
OpenAPI JSON: http://localhost:8080/v3/api-docs

### Tests

```bash
mvn clean test
```

Tests use an in-memory H2 database and mocked Redis operations. A live Redis server is **not** required to run the test suite.

---

## Phase 1 — Client management

### Endpoints

| Method | Path | Status | Description |
|---|---|---|---|
| `POST` | `/api/clients` | 201 | Register a client, generate API key |
| `GET` | `/api/clients` | 200 | List all clients |
| `GET` | `/api/clients/{id}` | 200 / 404 | Get a client by UUID |
| `PUT` | `/api/clients/{id}` | 200 / 404 | Update name, status, algorithm, and rate-limit settings |
| `DELETE` | `/api/clients/{id}` | 204 / 404 | Delete a client |

### Create client

```http
POST /api/clients
Content-Type: application/json

{
  "name": "client-app",
  "requestLimit": 100,
  "windowSeconds": 60,
  "algorithm": "SLIDING_WINDOW"
}
```

`requestLimit` and `windowSeconds` default to `100` and `60` if omitted in code constructors; they are required (`@NotNull`, `@Min(1)`) on the JSON payload. `algorithm` is optional on create/update and defaults to `FIXED_WINDOW` if omitted or null.

Response:

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "client-app",
  "apiKey": "ask_live_0123456789abcdef",
  "status": "ACTIVE",
  "requestLimit": 100,
  "windowSeconds": 60,
  "algorithm": "SLIDING_WINDOW",
  "createdAt": "2026-09-23T18:00:00Z",
  "updatedAt": "2026-09-23T18:00:00Z"
}
```

API keys are generated as `ask_live_` + 16 hex characters from `SecureRandom`.

### Validation

- `name` — required, max 200 characters
- `requestLimit` — required, must be greater than 0
- `windowSeconds` — required, must be greater than 0
- `algorithm` — optional, one of `FIXED_WINDOW`, `SLIDING_WINDOW`, `TOKEN_BUCKET` (defaults to `FIXED_WINDOW`)
- `status` — `ACTIVE` or `INACTIVE` (required on update)

Invalid payloads return `400 Bad Request` with a structured error body.

---

## Phase 2 — Fixed Window rate limiting

### How it works

Each client has a `requestLimit` and a `windowSeconds`. On every protected request:

1. Compute `windowId = epochSeconds / windowSeconds`
2. Redis key: `rate_limit:fixed:{apiKey}:{windowId}`
3. `INCR` the key
4. If the counter is `1` (first request in this window), set TTL = `windowSeconds`
5. If `count > requestLimit` → `429 Too Many Requests`
6. Keys expire automatically via Redis TTL — no manual deletion, no Lua scripts

```
Window 0  [t=0 ........ t=60)
  req 1  → 200  remaining=4
  req 2  → 200  remaining=3
  req 3  → 200  remaining=2
  req 4  → 200  remaining=1
  req 5  → 200  remaining=0
  req 6  → 429  remaining=0  Retry-After=<seconds until t=60>

Window 1  [t=60 ....... t=120)
  req 7  → 200  remaining=4   (counter reset)
```

### Demo endpoint

```http
GET /api/demo/products
X-API-Key: ask_live_0123456789abcdef
```

| Status | When |
|---|---|
| `200` | Valid, active key; under the limit |
| `401` | Missing / unknown API key |
| `403` | Client exists but `status = INACTIVE` |
| `429` | Limit exceeded for the current window |

Success and 429 responses both include:

| Header | Meaning |
|---|---|
| `X-RateLimit-Limit` | Max requests per window |
| `X-RateLimit-Remaining` | Requests left in this window |
| `X-RateLimit-Reset` | Seconds until the window rolls over |
| `Retry-After` | Same as reset (429 only) |

429 body:

```json
{
  "timestamp": "2026-09-23T18:00:05Z",
  "status": 429,
  "error": "Too Many Requests",
  "message": "Rate limit exceeded",
  "path": "/api/demo/products"
}
```

---

## Phase 3 — Centralized Filter Architecture

Phase 3 introduces `RateLimitFilter`, moving API key extraction, validation, and rate-limiting out of the individual controllers into a centralized Spring `OncePerRequestFilter`.

### Benefits

1. **Separation of concerns**: Controllers focus exclusively on business logic without repeating auth/rate-limit checks.
2. **Global enforcement**: Any new protected controller endpoint automatically inherits rate limiting. The filter executes exactly once per request.
3. **Direct Error Handling**: Standard HTTP 401, 403, and 429 JSON responses are written directly to `HttpServletResponse`, matching the `ErrorResponse` payload format.
4. **Selective paths**: Management and Swagger endpoints (e.g., `/api/clients`, `/swagger-ui`) bypass the filter, ensuring they remain freely accessible.

### Request Flow

1. Request hits `RateLimitFilter`
2. Checks exclusion paths (`shouldNotFilter`)
3. Extracts `X-API-Key` → 401 if missing/invalid
4. Verifies Client is `ACTIVE` → 403 if inactive
5. Calls `RateLimitService.checkRateLimit(apiKey, requestLimit, windowSeconds, algorithm)` → 429 if limit exceeded
6. Success → appends Rate-Limit headers, proceeds to controller
7. Controller returns business payload

---

## Phase 4 – Multiple Rate Limiting Algorithms

Phase 4 extends ReqForge to support multiple configurable rate limiting algorithms on a per-client basis.

### Algorithm Comparison

| Algorithm | Redis Structure | Main Concept |
|---|---|---|
| **Fixed Window** (`FIXED_WINDOW`) | String (`INCR` counter) | Requests are counted inside discrete time windows (`epochSeconds / windowSeconds`). Simple and memory-efficient, but susceptible to boundary traffic bursts. |
| **Sliding Window** (`SLIDING_WINDOW`) | Sorted Set (`ZSET`) | Requests are tracked over a rolling time period `[now - windowSeconds, now]`. Timestamps are scores, unique UUIDs are members. Eliminates window-boundary burst issues. |
| **Token Bucket** (`TOKEN_BUCKET`) | Hash (`HSET`) | Requests consume tokens from a bucket of capacity `requestLimit` refilled at `requestLimit / windowSeconds` tokens/sec. Lazily calculated on incoming requests. Supports controlled bursts. |

### Redis Key Patterns

- **Fixed Window**: `rate_limit:fixed:{apiKey}:{windowId}`
- **Sliding Window**: `rate_limit:sliding:{apiKey}`
- **Token Bucket**: `rate_limit:bucket:{apiKey}`

### Algorithm Details

#### Fixed Window
Requests are counted inside discrete time windows.
- Key: `rate_limit:fixed:{apiKey}:{windowId}` where `windowId = epochSeconds / windowSeconds`.
- Increments the counter via `opsForValue().increment(key)` and sets a TTL equal to `windowSeconds` on the first request.
- Remaining requests: `Math.max(0, requestLimit - count)`.
- Reset time: `((windowId + 1) * windowSeconds) - epochSeconds`.

#### Sliding Window
Requests are tracked over the most recent rolling time period.
- Key: `rate_limit:sliding:{apiKey}`.
- For every request:
  1. Remove expired request entries older than `now - windowSeconds` using `opsForZSet().rangeByScore(key, Double.NEGATIVE_INFINITY, now - windowSeconds)` and `remove(key, oldRequests)`.
  2. Count remaining timestamps in the window using `opsForZSet().size(key)`.
  3. If `count < requestLimit`, allow the request and add `(UUID.randomUUID().toString(), now)` to the sorted set. Set key expiration.
  4. If `count >= requestLimit`, reject the request.
- Remaining requests: `Math.max(0, requestLimit - updatedCount)`.
- Reset time: Seconds until the oldest request in the window expires (`(oldestScore + windowSeconds) - now`).

#### Token Bucket
Requests consume tokens while tokens gradually refill.
- Key: `rate_limit:bucket:{apiKey}` with fields `tokens` and `lastRefillSeconds`.
- Capacity: `requestLimit`.
- Refill Rate: `requestLimit / (double) windowSeconds` tokens per second.
- On each incoming request (lazy refill without background threads):
  1. Read `tokens` and `lastRefillSeconds` from Redis hash.
  2. If new bucket: start with full capacity `requestLimit` and `lastRefill = now`.
  3. If existing bucket: compute `elapsedSeconds = now - lastRefill`, calculate new tokens `available = Math.min(requestLimit, available + elapsedSeconds * refillRate)`.
  4. If `available >= 1`: consume 1 token (`available -= 1`), allow request.
  5. If `available < 1`: reject request.
  6. Save updated `tokens` and `lastRefillSeconds` back to Redis hash and set key TTL.
- Remaining requests: `(long) Math.floor(available)`.
- Reset time: `0` when allowed; when rejected, seconds required to accumulate enough tokens for at least 1 full request.

### Strategy Pattern Architecture

The rate limiting subsystem utilizes a clean Strategy Pattern decoupled from HTTP filters and controllers:

```
RateLimitFilter
      │
      ▼
RateLimitService
      │
      ▼
RateLimitStrategyFactory ──── selects ────► RateLimitAlgorithm (per Client)
      │
      ├───────────────────────────────┬───────────────────────────────┐
      ▼                               ▼                               ▼
FixedWindowRateLimitStrategy   SlidingWindowRateLimitStrategy   TokenBucketRateLimitStrategy
      │                               │                               │
      ▼ (String INCR)                 ▼ (Sorted Set ZSET)             ▼ (Hash HSET)
    Redis                           Redis                           Redis
```

- **`RateLimitStrategy`**: Common interface defining `RateLimitResult check(String apiKey, int requestLimit, int windowSeconds, Clock clock)`.
- **`RateLimitStrategyFactory`**: Selects and caches the concrete strategy based on `RateLimitAlgorithm` (defaults to `FixedWindowRateLimitStrategy` if algorithm is null).
- **`TimeConfig` / `Clock`**: Injected `java.time.Clock` enables deterministic unit testing without `Thread.sleep`.

### Known Concurrency / Atomicity Limitations

In Phase 4, the Sliding Window and Token Bucket algorithms execute multiple sequential Redis commands over the network (read → compute → write) without Lua scripts or multi-exec transactions:
- **Sliding Window**: Purging old entries, querying ZSET size, and adding the new timestamp member occur as separate Redis commands. Under heavy concurrent load on the exact same API key, a race condition can allow slightly more requests than `requestLimit`.
- **Token Bucket**: Reading `tokens` and `lastRefillSeconds`, computing refill/consumption, and writing back updated values occur across separate `HGET` / `HSET` calls. Under concurrent requests, simultaneous reads can read the same token count and both consume it (lost update race condition).

These multi-command operations do not provide distributed atomicity in Phase 4. Distributed atomic enforcement using Redis Lua scripts is deferred to subsequent phases.

---

## Project layout

```
src/main/java/com/apishield/
  ApiShieldApplication.java
  config/
    OpenApiConfig.java
    RedisConfig.java
    TimeConfig.java
  controller/
    ClientController.java
    DemoController.java
  dto/
    CreateClientRequest.java
    UpdateClientRequest.java
    ClientResponse.java
    RateLimitResult.java
  entity/
    Client.java
    ClientStatus.java
    RateLimitAlgorithm.java
  exception/
    ClientNotFoundException.java
    ClientInactiveException.java
    InvalidApiKeyException.java
    RateLimitExceededException.java
    GlobalExceptionHandler.java
    ErrorResponse.java
  filter/
    RateLimitFilter.java
  repository/
    ClientRepository.java
  service/
    ClientService.java
    RateLimitService.java
    RateLimitStrategy.java
    RateLimitStrategyFactory.java
    FixedWindowRateLimitStrategy.java
    SlidingWindowRateLimitStrategy.java
    TokenBucketRateLimitStrategy.java
```

---

## License

MIT

---

## License

MIT
