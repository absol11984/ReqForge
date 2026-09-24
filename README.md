# ReqForge – API Rate Limiting Service

ReqForge is a backend-focused REST API that will eventually provide production-grade API rate limiting. **Phase 1** focuses on a clean, working **client management** foundation backed by **PostgreSQL**.

## Phase 1 Features

- Client registration
- Client retrieval (all clients + by id)
- Client update (name + ACTIVE/INACTIVE status)
- Client deletion
- Server-generated **unique API key** per client
- Persistent storage of clients in PostgreSQL
- Jakarta Bean Validation + consistent JSON error responses
- Swagger/OpenAPI documentation

## Architecture

```text
Client
  ↓
REST Controller
  ↓
Service
  ↓
Repository
  ↓
PostgreSQL
```

## Tech Stack

- **Java 21**
- **Spring Boot 3.x**
- **Spring Web** (REST endpoints)
- **Spring Data JPA** (persistence)
- **PostgreSQL** (database)
- **Maven** (build)
- **Jakarta Validation** (request validation)
- **springdoc-openapi** (Swagger UI / OpenAPI docs)

## Database

Table: `clients`

| Column | Type | Notes |
|---|---|---|
| `id` | UUID | Primary key |
| `name` | VARCHAR | Client display name |
| `api_key` | VARCHAR | Unique generated API key |
| `status` | VARCHAR/ENUM | `ACTIVE` or `INACTIVE` |
| `created_at` | TIMESTAMP | Set on insert |
| `updated_at` | TIMESTAMP | Updated on change |

## API Documentation

Swagger UI:

- http://localhost:8080/swagger-ui.html

### Endpoints

#### 1) Create Client

- **POST** `/api/clients`

Request:

```json
{
  "name": "client-app"
}
```

Response: **201 Created**

```json
{
  "id": "uuid",
  "name": "client-app",
  "apiKey": "generated-api-key",
  "status": "ACTIVE",
  "createdAt": "timestamp",
  "updatedAt": "timestamp"
}
```

#### 2) Get All Clients

- **GET** `/api/clients`

Response: **200 OK**

```json
[
  { "id": "...", "name": "...", "apiKey": "...", "status": "ACTIVE", "createdAt": "...", "updatedAt": "..." }
]
```

#### 3) Get Client By ID

- **GET** `/api/clients/{id}`

Response:
- **200 OK** if found
- **404 Not Found** if not found

#### 4) Update Client

- **PUT** `/api/clients/{id}`

Request:

```json
{
  "name": "updated-client",
  "status": "ACTIVE"
}
```

Response: **200 OK**

#### 5) Delete Client

- **DELETE** `/api/clients/{id}`

Response:
- **204 No Content** if deleted
- **404 Not Found** if not found

### Error Response Shape

```json
{
  "timestamp": "2026-09-23T19:00:00",
  "status": 404,
  "error": "Client Not Found",
  "message": "Client with id ... was not found",
  "path": "/api/clients/..."
}
```

## Running Locally

### 1) PostgreSQL setup

Create a PostgreSQL database for the app (example name: `apishield`).

### 2) Environment variables

The application reads database credentials from environment variables:

- `DB_URL` (e.g. `jdbc:postgresql://localhost:5432/apishield`)
- `DB_USERNAME`
- `DB_PASSWORD`

Optional:
- `SERVER_PORT` (defaults to `8080`)

### 3) Start with Maven

```bash
mvn spring-boot:run
```

### 4) Swagger

Open:

- http://localhost:8080/swagger-ui.html

## Future Phases

- **Phase 2 →** Redis + Fixed Window Rate Limiting
- **Phase 3 →** Request Filtering / Middleware
- **Phase 4 →** Sliding Window + Token Bucket
- **Phase 5 →** Concurrency + Atomic Redis Operations
- **Phase 6 →** Metrics + Production Improvements
