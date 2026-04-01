# Payment Wallet Service

A production-grade REST API for digital wallet management — built to
understand the engineering challenges behind payment systems like
PhonePe, Razorpay, and Paytm.

## What this project demonstrates

| Problem | Solution |
|---|---|
| Double-spend under concurrent transfers | Pessimistic locking (`SELECT FOR UPDATE`) in consistent UUID order |
| Duplicate API calls from network retries | Mandatory idempotency keys with DB-level unique constraint |
| Stateless authentication | JWT filter in Spring Security — no sessions |
| Balance read latency at scale | Redis cache-aside pattern with manual eviction on write |
| Tight coupling between transfer and notifications | Kafka event publishing — consumers are independent |
| API abuse and infrastructure protection | Token bucket rate limiting via Redis atomic DECREMENT |
| Production visibility | MDC correlation IDs, Micrometer counters, Spring Actuator |

## Tech stack

- **Java 21** + **Spring Boot 3.2**
- **PostgreSQL** — ACID transactions, pessimistic locking, Flyway migrations
- **Redis** — cache-aside for balance reads, token bucket rate limiting
- **Kafka** — async event publishing after transfer, notification + audit consumers
- **JWT** — stateless auth via Spring Security filter chain
- **Testcontainers** — integration tests against real Postgres, Redis, Kafka
- **Docker** — multi-stage build, docker-compose for full local setup

## API endpoints

### Auth (public)
```
POST /api/auth/register   — create user + wallet
POST /api/auth/login      — get JWT token
```

### Wallet (requires Authorization: Bearer <token>)
```
GET  /api/wallet/balance         — current balance (Redis cached)
POST /api/wallet/credit          — add money
POST /api/wallet/transfer        — send money (requires Idempotency-Key header)
GET  /api/wallet/transactions    — history with counterparty names
```

### Observability
```
GET /actuator/health    — DB + Redis + Kafka health
GET /actuator/metrics   — HTTP + custom business metrics
```

## Run locally

**Prerequisites:** Java 21, Docker Desktop
```bash
# clone and start infrastructure
git clone https://github.com/your-username/payment-wallet
cd payment-wallet
docker-compose up -d postgres redis kafka

# run the app
./mvnw spring-boot:run

# or run everything including the app
docker-compose up -d
```

## Key design decisions

### Pessimistic locking for transfers
Transfer acquires row-level lock on both wallets using `SELECT FOR UPDATE`
before checking balance. Wallets are always locked in UUID order to prevent
deadlock when two transfers involve the same pair of wallets concurrently.

### Idempotency
`Idempotency-Key` header is mandatory for transfers. Enforced at application
level (early return) and database level (UNIQUE constraint). A duplicate
request returns the original transaction response without re-processing.

### Cache-aside with explicit eviction
Used `StringRedisTemplate` directly instead of Spring Cache abstraction for
full control over serialization and eviction timing. Cache is evicted
explicitly after credit and transfer — never updated directly. If Redis
fails, the app degrades gracefully to hitting Postgres.

### Kafka for decoupling
Transfer API publishes a `TransactionEvent` after DB commit. Notification
and audit consumers have separate group IDs so both receive every event
independently. Publish is async — transfer response doesn't wait for Kafka.

### Known limitation: dual-write problem
If the DB commits but Kafka publish fails, the event is lost. Production fix
is the Transactional Outbox Pattern — write event to a DB table first, relay
to Kafka via a separate process. Acknowledged but not implemented here.

## Testing
```bash
# unit tests (fast, no Docker needed)
./mvnw test -Dtest="WalletServiceTest"

# integration tests (requires Docker Desktop)
./mvnw test
```

Unit tests cover: transfer business logic, idempotency, insufficient balance,
self-transfer rejection.

Integration tests cover: full HTTP flow with real Postgres + Redis + Kafka
via Testcontainers.