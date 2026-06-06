## Why

Ticket Monster needs a backend system capable of handling 50M DAU and 5M concurrent users during massive event sales, with a strict guarantee of zero overbooking. The current state is two empty Spring Boot 4 projects (`ticketmonster` monolith and `api-gateway`). The entire backend — from domain modules to infrastructure, observability, and deployment — must be built from scratch.

## What Changes

- Implement a modular monolith (Spring Modulith) with 4 DDD bounded contexts: Catalog, Virtual Queue, Reservation, and Payment
- Add an API Gateway (Spring Cloud Gateway) as the single entry point with JWT validation, rate limiting, and routing
- Integrate Redpanda for async event-driven communication between modules (reservation lifecycle, payment confirmation)
- Implement distributed locking in Redis for anti-overbooking (pessimistic reservation with TTL-based expiration)
- Implement a virtual queue (Redis FIFO) with batch dispatcher for massive event sale openings
- Set up polyglot persistence: MongoDB (Catalog), PostgreSQL (Reservation + Payment), Redis (Queue + Locks)
- Secure the system with Keycloak (OAuth2/OIDC) deployed on K3s
- Add full observability stack: Loki (logs), Prometheus + Micrometer (metrics), Tempo + OpenTelemetry (traces), Grafana (dashboards)
- Create 3 idempotent provisioning scripts (`provision-k3s.sh`, `provision-infra.sh`, `provision-services.sh`) and Helm charts
- Add k6 load testing scripts to validate the system under concurrency

## Capabilities

### New Capabilities

- `event-catalog`: Event, venue, artist, and zone management with GraphQL API backed by MongoDB. Covers listing, searching, and availability queries.
- `virtual-queue`: Redis-based FIFO queue with batch dispatcher for high-demand event sales. Controls gradual user access to the reservation system.
- `ticket-reservation`: Temporary ticket reservation (10-min TTL) with distributed locks in Redis and atomic stock decrement in PostgreSQL. Core anti-overbooking logic.
- `payment-processing`: Checkout flow that converts a temporary reservation into a confirmed sale. Idempotent, ACID-compliant on PostgreSQL.
- `api-gateway`: Spring Cloud Gateway with Keycloak JWT validation, Resilience4j rate limiting, and module routing.
- `observability`: Full observability stack — Loki/Logback (logs), Prometheus/Micrometer (metrics), Tempo/OpenTelemetry (traces), Grafana (dashboards).
- `deployment`: K3s infrastructure provisioning scripts, Helm charts for all services and infra, and k6 load testing scripts.
- `local-development`: Docker Compose setup for local development with all infrastructure dependencies, pre-configured Keycloak realm, Grafana dashboards, and health checks.

### Modified Capabilities

_(none — this is a greenfield implementation)_

## Impact

- **Code**: Two Spring Boot 4 projects (`backend/ticketmonster`, `backend/api-gateway`) will be fully implemented from empty scaffolds
- **APIs**: New GraphQL API (Catalog) and REST APIs (Queue, Reservation, Payment, Gateway) — all greenfield
- **Dependencies**: Spring Modulith, Spring for GraphQL, Spring Cloud Gateway, Spring Data MongoDB/JPA, Spring Security OAuth2 Resource Server, Resilience4j, Redpanda Kafka client, Lettuce (Redis), Micrometer, OpenTelemetry
- **Infrastructure**: K3s cluster on remote VPS, Helm charts for Redpanda, Redis, PostgreSQL, MongoDB, Keycloak, cert-manager, Grafana stack
- **External systems**: Keycloak (auth), payment gateway (webhooks for payment confirmation)
