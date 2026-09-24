# APIShield

API rate limiting service built with Java 21 and Spring Boot 3.3.2.

- **Phase 1** — Client management REST API with PostgreSQL persistence
- **Phase 2** — Redis-backed Fixed Window rate limiting

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
- Redis 6+ (Phase 2)

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

Tests use an in-memory H2 database and a mocked Redis template. A live Redis server is **not** required to run the test suite.

---

## Phase 1 — Client management

### Endpoints

| Method | Path | Status | Description |
|---|---|---|---|
| `POST` | `/api/clients` | 201 | Register a client, generate API key |
| `GET` | `/api/clients` | 200 | List all clients |
| `GET` | `/api/clients/{id}` | 200 / 404 | Get a client by UUID |
| `PUT` | `/api/clients/{id}` | 200 / 404 | Update name, status, and rate-limit settings |
| `DELETE` | `/api/clients/{id}` | 204 / 404 | Delete a client |

### Create client

```http
POST /api/clients
Content-Type: application/json

{
  "name": "client-app",
  "requestLimit": 5,
  "windowSeconds": 60
}
```

`requestLimit` and `windowSeconds` default to `100` and `60` if omitted in code constructors; they are required (`@NotNull`, `@Min(1)`) on the JSON payload.

Response:

```json
{
  "id": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "name": "client-app",
  "apiKey": "ask_live_0123456789abcdef",
  "status": "ACTIVE",
  "requestLimit": 5,
  "windowSeconds": 60,
  "createdAt": "2026-09-23T18:00:00Z",
  "updatedAt": "2026-09-23T18:00:00Z"
}
```

API keys are generated as `ask_live_` + 16 hex characters from `SecureRandom`.

### Validation

- `name` — required, max 200 characters
- `requestLimit` — required, must be greater than 0
- `windowSeconds` — required, must be greater than 0
- `status` — `ACTIVE` or `INACTIVE` (required on update)

Invalid payloads return `400 Bad Request` with a structured error body.

---

## Phase 2 — Fixed Window rate limiting

### How it works

Each client has a `requestLimit` and a `windowSeconds`. On every protected request:

1. Compute `windowId = epochSeconds / windowSeconds`
2. Redis key: `rate_limit:{apiKey}:{windowId}`
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

### Architecture

```
Client
  │  X-API-Key
  ▼
DemoController
  │  lookup by apiKey          RateLimitService
  ▼                            │
ClientService ──► PostgreSQL   │  INCR rate_limit:{apiKey}:{windowId}
                               ▼
                             Redis
                               │  TTL = windowSeconds (set on first hit)
                               ▼
                       RateLimitResult
                       (allowed, limit, remaining, resetSeconds)
```

PostgreSQL stores durable client data. Redis stores only the short-lived counters.

---

## Project layout

```
src/main/java/com/apishield/
  ApiShieldApplication.java
  config/
    OpenApiConfig.java
    RedisConfig.java
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
  exception/
    ClientNotFoundException.java
    ClientInactiveException.java
    InvalidApiKeyException.java
    RateLimitExceededException.java
    GlobalExceptionHandler.java
    ErrorResponse.java
  repository/
    ClientRepository.java
  service/
    ClientService.java
    RateLimitService.java
```

---

## License

MIT
