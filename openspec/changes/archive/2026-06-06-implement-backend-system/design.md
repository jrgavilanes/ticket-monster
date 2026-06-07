## Context

Two empty Spring Boot 4 projects exist under `backend/`: `ticketmonster` (modular monolith) and `api-gateway`. The system must support 50M DAU and 5M concurrent users during massive event sales with zero overbooking. The primary deliverable is technical documentation and architectural analysis; code validates the architecture.

The system follows DDD with 4 bounded contexts implemented as Spring Modulith modules inside a single deployable unit. Communication is hybrid: synchronous (GraphQL/REST) for queries, asynchronous (Redpanda events) for state changes.

## Goals / Non-Goals

**Goals:**
- Guarantee zero overbooking through pessimistic distributed locking (Redis `SET NX EX` + PostgreSQL `SELECT FOR UPDATE`)
- Support 5M concurrent users during sale openings via virtual queue with batch dispatching
- Modular monolith with clean boundaries that can be extracted to microservices independently
- Full observability (logs, metrics, traces) from day one
- Idempotent, repeatable infrastructure provisioning via scripts and Helm charts
- Document every architectural decision with trade-off analysis

**Non-Goals:**
- Building a production-ready payment gateway integration (mock/stub is acceptable)
- Implementing a full frontend or mobile app
- Multi-region deployment or geo-replication
- Real-time WebSocket push for queue status (polling is acceptable for now)
- Implementing admin UI for catalog management (API only)

## Decisions

### 1. Monolith-First with Spring Modulith over Microservices

**Decision**: Start with a modular monolith using Spring Modulith, designed for future extraction.

**Alternatives considered**:
- **Microservices from day one**: Rejected — adds operational complexity (service mesh, distributed tracing, network failures) without proven need. For a team building from scratch, the coordination overhead slows development.
- **Pure monolith without modules**: Rejected — no clear boundaries makes future extraction painful.

**Rationale**: Spring Modulith enforces module boundaries at compile/test time. Modules communicate via an internal event bus that maps directly to Redpanda when extracted. This follows Martin Fowler's "monolith first" strategy. The cost of extraction later is low because async communication is already established.

### 2. Pessimistic Locking (Redis + PostgreSQL) over Optimistic Locking

**Decision**: Use Redis distributed locks (`SET reservation:{eventId}:{seatId} {userId} EX 600 NX`) as the primary lock, backed by PostgreSQL `SELECT FOR UPDATE` as the source of truth.

**Alternatives considered**:
- **Optimistic locking (version columns)**: Rejected — under 5M concurrent users, optimistic locking produces massive retry storms. The contention on popular events makes optimistic locking impractical.
- **PostgreSQL-only advisory locks**: Rejected — doesn't survive PostgreSQL restarts and ties lock lifecycle to DB connections, which are expensive at scale.

**Rationale**: Redis `SET NX EX` is atomic, survives application restarts, and auto-expires via TTL. PostgreSQL serves as the durable source of truth. The dual-layer approach means: fast rejection at Redis level (most requests), durable confirmation at PostgreSQL level (confirmed reservations). TTL expiration triggers automatic stock release via Redis keyspace notifications → Redpanda event.

### 3. Redis FIFO Queue with Batch Dispatcher over Kafka-based Queue

**Decision**: Virtual queue lives entirely in Redis (LPUSH/BRPOP), with a Spring `@Scheduled` dispatcher releasing batches of N users every X seconds.

**Alternatives considered**:
- **Redpanda-based queue**: Rejected — overkill for ephemeral queue state. If the queue is lost, users rejoin. Redpanda is better suited for durable event streams.
- **Dedicated queue service (SQS/RabbitMQ)**: Rejected — adds another infrastructure dependency. Redis is already required for locks.

**Rationale**: Redis lists are O(1) for push/pop. The queue is ephemeral by design (if Redis dies, the queue rebuilds). The batch dispatcher provides backpressure control: releasing 500 users every 2 seconds means the Reservation Module never faces more than 250 req/s from the queue, regardless of how many millions are waiting.

### 4. Polyglot Persistence (MongoDB + PostgreSQL + Redis)

**Decision**: Each module owns its data store based on access patterns.

| Module | Store | Rationale |
|---|---|---|
| Catalog | MongoDB | Flexible schema for diverse event types, read-heavy with complex nested queries, horizontal scaling via sharding |
| Reservation | PostgreSQL | ACID transactions, `SELECT FOR UPDATE` for row-level locking, strong consistency required |
| Payment | PostgreSQL | Financial transactions require ACID, audit trails, idempotency keys |
| Virtual Queue | Redis only | Ephemeral data, no persistence needed, sub-millisecond latency |

**Alternatives considered**:
- **Single database for all modules**: Rejected — forces compromise. Catalog needs flexible schemas; Reservation needs strong consistency. One DB can't optimize both.
- **PostgreSQL for Catalog**: Considered but rejected — event schemas vary significantly (concerts vs sports vs theater), and MongoDB's document model maps naturally to these variations.

### 5. GraphQL for Catalog, REST for Transactional Modules

**Decision**: Catalog exposes GraphQL (Spring for GraphQL). Queue, Reservation, and Payment expose REST.

**Alternatives considered**:
- **GraphQL for everything**: Rejected — GraphQL mutations don't map well to transactional workflows with side effects (payment confirmation, queue management). REST's uniform interface is simpler for state-changing operations.
- **gRPC for internal communication**: Rejected — modules are in the same process (monolith). gRPC adds serialization overhead with no benefit for in-process calls.

**Rationale**: Catalog is read-heavy with complex relationships (events → venues → zones → availability). GraphQL eliminates over-fetching and allows clients to request exactly what they need. Transactional modules benefit from REST's simplicity and standard HTTP semantics (idempotency via PUT/DELETE).

### 6. Keycloak as Identity Provider

**Decision**: Keycloak deployed on K3s as the OAuth2/OIDC provider.

**Alternatives considered**:
- **Auth0/Cognito**: Rejected — external dependency, recurring cost, and the project requires self-hosted infrastructure for documentation purposes.
- **Custom auth service**: Rejected — reinventing auth is a security risk. Keycloak is battle-tested and supports OAuth2, OIDC, and social login out of the box.

**Rationale**: Keycloak is self-hosted, open-source, and integrates natively with Spring Security via `spring-boot-starter-oauth2-resource-server`. JWTs are validated at the API Gateway level; the monolith trusts the gateway's forwarded headers.

### 7. Redpanda over Apache Kafka

**Decision**: Redpanda as the event streaming platform.

**Alternatives considered**:
- **Apache Kafka**: Considered — but requires JVM, ZooKeeper (or KRaft), and more operational overhead.
- **RabbitMQ**: Rejected — doesn't provide the log-based replay and partitioning model needed for event sourcing patterns.

**Rationale**: Redpanda is Kafka-API-compatible, written in C++ (no JVM), uses Raft consensus (no ZooKeeper), and has lower latency. For a system that documents architectural decisions, Redpanda simplifies operations while maintaining Kafka ecosystem compatibility.

### 8. Resilience4j for All Resilience Patterns

**Decision**: Single library (Resilience4j) for circuit breaker, rate limiter, retry, timeout, and bulkhead across all modules.

**Alternatives considered**:
- **Spring Retry + custom circuit breakers**: Rejected — fragmented approach, harder to configure consistently.
- **Istio/Envoy for resilience**: Rejected — adds service mesh complexity. In a modular monolith, in-process resilience is sufficient.

**Rationale**: Resilience4j is the de facto standard for Spring Boot resilience. It integrates with Spring Boot Actuator and Micrometer for metrics export. Each module configures its own circuit breaker and rate limiter instances, providing isolation (bulkhead pattern).

## Risks / Trade-offs

**[Redis single point of failure for locks and queue]** → Mitigation: Deploy Redis Sentinel for HA. If Redis is completely lost, locks expire (no overbooking because PostgreSQL is source of truth) and the queue rebuilds. Document this trade-off explicitly.

**[Monolith scaling limits]** → Mitigation: HPA scales pods horizontally. Each pod is a full monolith instance. If a specific module needs independent scaling, extract it to a microservice — the async communication via Redpanda is already in place.

**[PostgreSQL connection pool exhaustion under 5M concurrent users]** → Mitigation: The virtual queue absorbs the burst. Only batched users (500 every 2s) reach the Reservation Module. Connection pool is sized for batch throughput, not total queue size.

**[MongoDB eventual consistency for Catalog reads]** → Mitigation: Catalog is read-heavy and tolerates slight staleness. For availability counts (which must be accurate), the Reservation Module queries PostgreSQL directly, not MongoDB.

**[Keycloak as single auth dependency]** → Mitigation: Keycloak is deployed with multiple replicas on K3s. JWT validation at the gateway is stateless (public key verification), so brief Keycloak outages don't affect already-authenticated users.

**[TTL-based reservation expiration race condition]** → Mitigation: Redis keyspace notification fires on expiration, but is not guaranteed (Redis docs). Fallback: a periodic sweep job in Reservation Module checks for expired reservations in PostgreSQL that weren't cleaned up by the event path.
