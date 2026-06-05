## Why

Build a high-concurrency ticket reservation system (Ticket Monster) capable of handling 50M DAU and 5M concurrent users during mass event sales. The system must guarantee zero overbooking while providing a controlled, fair purchasing experience through a virtual queue. The primary deliverable is technical documentation and architectural analysis demonstrating mastery of DDD, CAP theorem, resilience, concurrency, and event-driven architecture.

## What Changes

- Create a Spring Boot + Spring Modulith monolith with four bounded contexts: Catalog, Virtual Queue, Reservation, and Payment
- Implement anti-overbooking strategy using pessimistic locking with Redis distributed locks (TTL 10 min) and PostgreSQL `SELECT FOR UPDATE`
- Build a Redis-based FIFO virtual queue with batch dispatcher for controlled access during high-demand sales
- Set up hybrid inter-module communication: synchronous (GraphQL/REST) for queries, asynchronous (Redpanda events) for state changes
- Deploy on K3s with three idempotent provisioning scripts (k3s setup, infrastructure, services)
- Integrate Keycloak for OAuth2/OIDC authentication with Spring Cloud Gateway as API Gateway
- Add full observability stack: Loki (logs), Prometheus/Micrometer (metrics), Tempo/OpenTelemetry (traces), Grafana (dashboards)
- Configure Resilience4j patterns: circuit breaker, rate limiter, retry, timeout, bulkhead per module
- Create load testing suite with k6 and oha
- Generate architectural documentation with Mermaid diagrams (architecture overview, DDD context map, purchase flow, anti-overbooking, virtual queue, deployment)

## Capabilities

### New Capabilities

- `event-catalog`: Event, venue, artist, and availability management with MongoDB and GraphQL API. Read-heavy module with flexible schema for different event types.
- `virtual-queue`: Redis FIFO queue with batch dispatcher for controlled access during mass sales. Issues temporary JWT access tokens when users reach the front.
- `ticket-reservation`: Pessimistic reservation with Redis distributed locks and PostgreSQL ACID transactions. Core anti-overbooking logic with 10-minute TTL expiry.
- `payment`: Checkout flow converting reservations into confirmed sales. Idempotent payment processing with webhook confirmation via Redpanda events.
- `api-gateway`: Spring Cloud Gateway with JWT validation, rate limiting (Resilience4j), and module routing. Single entry point for all client traffic.
- `observability`: Full observability stack with structured logging (Loki/Logback), metrics (Prometheus/Micrometer), distributed tracing (Tempo/OpenTelemetry), and Grafana dashboards.
- `infrastructure`: K3s-based deployment with Helm charts, three idempotent provisioning scripts, and HPA autoscaling. Covers cert-manager, Redpanda, Redis, PostgreSQL, MongoDB, and Grafana stack.

### Modified Capabilities

_(none — greenfield project)_

## Impact

- **Code**: Entire codebase is new — Spring Boot monolith with four modules, API Gateway, provisioning scripts, Helm charts, k6 test scripts
- **APIs**: GraphQL (Catalog), REST (Queue, Reservation, Payment) — all behind API Gateway
- **Dependencies**: Spring Boot, Spring Modulith, Spring Cloud Gateway, Spring for GraphQL, Redpanda, Redis, PostgreSQL, MongoDB, Keycloak, Resilience4j, OpenTelemetry, k6, Helm, K3s
- **Systems**: Remote VPS (Debian 12) with K3s cluster, multiple namespaces for infrastructure and application
- **Documentation**: README with 6 Mermaid diagrams, architectural decision records, evaluation questionnaire responses
