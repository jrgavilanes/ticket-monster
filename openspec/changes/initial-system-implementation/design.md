## Context

Ticket Monster is a greenfield high-concurrency ticket reservation system targeting 50M DAU and 5M concurrent users during mass event sales. The system must guarantee zero overbooking while providing fair access through a virtual queue. The project's primary deliverable is technical documentation and architectural analysis; code serves to validate the architecture.

The system is structured as a Spring Modulith monolith with four bounded contexts (Catalog, Virtual Queue, Reservation, Payment), deployed on K3s with full observability and resilience patterns.

**Constraints:**
- Single deployable unit (monolith) with clean module boundaries for future extraction
- Must handle extreme traffic spikes (event sale openings) without overbooking
- Documentation-first: every architectural decision must be justified
- Three idempotent provisioning scripts for reproducible infrastructure

## Goals / Non-Goals

**Goals:**
- Guarantee zero overbooking through pessimistic locking (Redis distributed locks + PostgreSQL `SELECT FOR UPDATE`)
- Absorb 5M concurrent users via Redis FIFO virtual queue with batch dispatcher
- Provide flexible event catalog with GraphQL API for complex queries
- Enable secure, rate-limited access through API Gateway with Keycloak OAuth2
- Achieve full observability (logs, metrics, traces) with Grafana stack
- Deploy reproducible infrastructure with idempotent scripts on K3s
- Document all architectural decisions with trade-off analysis

**Non-Goals:**
- Building a production-ready payment gateway integration (mock/stub payment provider)
- Mobile app or frontend UI development
- Multi-region deployment or geo-distribution
- Real-time WebSocket push for queue status (polling-based initially)
- Admin dashboard UI (API-only for catalog management)
- Data migration or integration with existing ticketing systems

## Decisions

### 1. Monolith Modular over Microservices (Spring Modulith)

**Choice:** Spring Modulith monolith with four internal modules communicating via direct calls and Redpanda events.

**Alternatives considered:**
- **Microservices from day one**: More operational complexity (service mesh, distributed tracing overhead, network failures, deployment pipelines per service). Overkill for a team building an initial system.
- **Serverless/functions**: Poor fit for stateful reservation logic requiring distributed locks and long-lived transactions.

**Rationale:** Following "monolith first, microservices later" (Fowler/Newman). Spring Modulith enforces module boundaries via architecture tests. Modules can be extracted to microservices when scaling needs demand it, without rewriting communication patterns since async messaging via Redpanda is already in place.

### 2. Redis Distributed Locks + PostgreSQL for Anti-Overbooking

**Choice:** Two-phase reservation: Redis `SET NX EX 600` for fast exclusive lock, PostgreSQL `SELECT FOR UPDATE` for durable stock verification.

**Alternatives considered:**
- **Optimistic locking only (version columns)**: High contention during mass sales leads to excessive retry storms and poor UX.
- **Redis-only (no DB verification)**: Risk of data loss if Redis crashes. No durable audit trail.
- **Database-only (no Redis)**: PostgreSQL row locks don't scale to 5M concurrent users. Connection pool exhaustion.

**Rationale:** Redis handles the high-throughput fast path (lock acquisition at memory speed), PostgreSQL provides the durable source of truth. The 10-minute TTL auto-releases abandoned reservations. This dual approach balances performance and consistency (CP in CAP terms for the reservation bounded context).

### 3. Redis FIFO Queue with Batch Dispatcher for Virtual Queue

**Choice:** Redis List (LPUSH/BRPOP) as FIFO queue. A scheduled dispatcher releases batches of N users every X seconds, issuing short-lived JWT access tokens.

**Alternatives considered:**
- **Kafka/Redpanda as queue**: Overkill for ephemeral queue state. Adds persistence overhead for data that is inherently transient.
- **Database-backed queue**: Too slow for 5M concurrent enqueue operations. Disk I/O bottleneck.
- **Token bucket / leaky bucket only**: Doesn't provide fair FIFO ordering, which is critical for user perception of fairness.

**Rationale:** Redis Lists provide O(1) enqueue/dequeue. The queue is ephemeral by design — if Redis crashes, the queue rebuilds (acceptable trade-off, AP in CAP). Batch dispatching provides backpressure protection for downstream reservation module. JWT tokens prevent queue bypass.

### 4. Redpanda over Kafka for Event Streaming

**Choice:** Redpanda as Kafka-compatible event broker.

**Alternatives considered:**
- **Apache Kafka**: Requires JVM, ZooKeeper (or KRaft), more operational complexity. Higher resource footprint.
- **RabbitMQ**: AMQP-based, not log-structured. Poor fit for event sourcing and replay scenarios.
- **NATS JetStream**: Smaller ecosystem, fewer enterprise features.

**Rationale:** Redpanda is Kafka API-compatible (existing Spring Cloud Stream / Kafka clients work), written in C++ (lower latency, no JVM), no ZooKeeper dependency (Raft consensus), and simpler to operate on K3s. For documentation purposes, all Kafka ecosystem knowledge transfers directly.

### 5. MongoDB for Catalog, PostgreSQL for Reservation/Payment

**Choice:** Polyglot persistence — MongoDB for the read-heavy, flexible-schema Catalog module; PostgreSQL for the transactional Reservation and Payment modules.

**Alternatives considered:**
- **PostgreSQL for everything**: Catalog has diverse event types with varying attributes (sports vs. concerts vs. theater). Rigid schema leads to sparse columns or JSONB abuse.
- **MongoDB for everything**: Reservation requires ACID transactions with `SELECT FOR UPDATE` semantics. MongoDB's multi-document transactions are weaker and don't provide row-level locking.

**Rationale:** Each database is chosen for its fitness to the bounded context's access patterns. Catalog is read-heavy with flexible schemas (AP). Reservation/Payment require strong consistency and transactional guarantees (CP). This demonstrates understanding of CAP theorem trade-offs.

### 6. GraphQL for Catalog, REST for Transactional Modules

**Choice:** Spring for GraphQL for Catalog queries; REST for Queue, Reservation, and Payment.

**Alternatives considered:**
- **REST for everything**: Over-fetching for complex catalog queries (events with nested venues, artists, dates, availability). Multiple round trips.
- **GraphQL for everything**: Overkill for simple transactional operations (create reservation, process payment). Adds complexity for mutations that map naturally to REST verbs.

**Rationale:** GraphQL excels at read-heavy, nested data queries where clients need different projections. REST is simpler and more idiomatic for transactional CRUD operations and webhook integrations (payment providers expect REST).

### 7. Keycloak + Spring Cloud Gateway for Security

**Choice:** Keycloak as IdP (OAuth2/OIDC), Spring Cloud Gateway as API Gateway validating JWTs, Resilience4j rate limiting at the edge.

**Alternatives considered:**
- **Auth0/Okta**: SaaS dependency, cost at scale (50M DAU), less control for documentation purposes.
- **Custom auth service**: Reinventing the wheel, security risk, not a differentiator for the project.
- **No API Gateway**: Direct module exposure means no centralized rate limiting, no single point for JWT validation.

**Rationale:** Keycloak is self-hosted (fits K3s deployment), supports OAuth2/OIDC natively, and integrates with Spring Security. The API Gateway provides a single entry point for cross-cutting concerns (auth, rate limiting, routing), following the BFF/Gateway pattern.

### 8. Resilience4j for Module-Level Resilience

**Choice:** Resilience4j circuit breaker, rate limiter, retry, timeout, and bulkhead applied per module within the monolith.

**Alternatives considered:**
- **Istio/Linkerd service mesh**: Only applicable if modules were separate services. Adds operational complexity disproportionate to a monolith.
- **Spring Retry only**: Missing circuit breaker and bulkhead patterns. Insufficient for comprehensive resilience.

**Rationale:** Resilience4j is the de facto standard for Spring Boot resilience. Works within a monolith (no service mesh needed). Each pattern addresses a specific failure mode: circuit breaker (cascade prevention), rate limiter (spike protection), retry (transient failures), timeout (hanging calls), bulkhead (resource isolation).

## Risks / Trade-offs

**[Redis single point of failure for locks and queue]** → Mitigation: Redis Sentinel or Redis Cluster for HA. Queue is ephemeral and rebuilds. Locks have TTL so stale locks auto-expire. Document this as a known trade-off.

**[Monolith scaling ceiling]** → Mitigation: HPA scales pod replicas horizontally. Module boundaries are clean for future extraction. Document extraction criteria and process.

**[PostgreSQL connection pool exhaustion under extreme load]** → Mitigation: Virtual queue absorbs the spike, batch dispatcher limits concurrent reservation requests to a sustainable rate. HikariCP tuning with appropriate pool sizes.

**[MongoDB eventual consistency for catalog]** → Mitigation: Catalog is read-heavy and tolerates slight staleness (AP). Write operations (admin) are low-frequency. Read-your-writes consistency for admin operations via write concern configuration.

**[Redpanda event loss during broker failure]** → Mitigation: Configure replication factor ≥ 3, `acks=all` for producers, dead letter queues for failed consumers. Idempotent consumers handle duplicate delivery.

**[Keycloak availability as auth bottleneck]** → Mitigation: Keycloak deployed with multiple replicas on K3s. JWT validation in the monolith uses cached public keys (JWKS), so auth continues briefly even if Keycloak is temporarily unavailable.

**[Provisioning scripts assume single VPS]** → Mitigation: Scripts are idempotent and documented for single-node K3s. Multi-node cluster is a documented future enhancement.
