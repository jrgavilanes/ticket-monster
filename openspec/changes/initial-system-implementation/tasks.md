## 1. Project Scaffolding

- [ ] 1.1 Initialize Spring Boot project with Spring Modulith, Gradle build, and Java 21
- [ ] 1.2 Configure module structure: catalog, queue, reservation, payment under `src/main/java`
- [ ] 1.3 Add Spring Modulith architecture tests to verify module boundaries
- [ ] 1.4 Configure application.yml with profiles (dev, prod) and environment variable placeholders
- [ ] 1.5 Add Dockerfile for the monolith application
- [ ] 1.6 Create Helm chart skeleton for ticket-monster under `deploy/charts/ticket-monster/`
- [ ] 1.7 Create Helm chart skeleton for api-gateway under `deploy/charts/api-gateway/`

## 2. Infrastructure Provisioning

- [ ] 2.1 Create `provision-k3s.sh` — install K3s, k9s, Helm on remote Debian 12 VPS with kubeconfig setup
- [ ] 2.2 Create `provision-infra.sh` — deploy cert-manager, Redpanda, Redis, PostgreSQL, MongoDB, Grafana stack via Helm with idempotent secret generation
- [ ] 2.3 Create `provision-services.sh` — build Docker image, helm upgrade --install for monolith and API Gateway, run k6 tests
- [ ] 2.4 Create Helm values files for each infrastructure component under `deploy/infra/`
- [ ] 2.5 Configure cert-manager ClusterIssuer with Let's Encrypt for TLS

## 3. Database Layer

- [ ] 3.1 Define PostgreSQL schema for Reservation module (reservations, reservation_items, ticket_locks tables) with Flyway migrations
- [ ] 3.2 Define PostgreSQL schema for Payment module (payments, payment_transactions tables) with Flyway migrations
- [ ] 3.3 Define MongoDB document structure for Catalog module (venues, events, artists, event_dates, zones collections)
- [ ] 3.4 Configure Spring Data JPA repositories for Reservation and Payment modules
- [ ] 3.5 Configure Spring Data MongoDB repositories for Catalog module
- [ ] 3.6 Create seed data scripts for development (sample venues, events, artists)

## 4. Catalog Module

- [ ] 4.1 Implement domain entities: Venue, Event, Artist, EventDate, Zone/Section
- [ ] 4.2 Implement MongoDB repositories and service layer for catalog CRUD operations
- [ ] 4.3 Define GraphQL schema (schema.graphqls) with queries: events, event(id), searchEvents(query), availability(eventId)
- [ ] 4.4 Implement GraphQL resolvers (DataFetcher/Controller) for all catalog queries
- [ ] 4.5 Implement search functionality with filters (name, artist, venue, date range, event type)
- [ ] 4.6 Implement real-time availability calculation per zone (total - reserved - sold)
- [ ] 4.7 Add ADMIN role authorization for catalog mutations (create, update, delete)
- [ ] 4.8 Write integration tests for Catalog module GraphQL queries

## 5. Virtual Queue Module

- [ ] 5.1 Implement Redis FIFO queue using List operations (LPUSH/BRPOP) with deduplication check
- [ ] 5.2 Implement `POST /api/v1/queue/{eventId}/join` endpoint — enqueue user, return ticket ID and position
- [ ] 5.3 Implement `GET /api/v1/queue/{eventId}/status` endpoint — return position, queue size, estimated wait
- [ ] 5.4 Implement `GET /api/v1/queue/{eventId}/token` endpoint — issue short-lived JWT when user is at front
- [ ] 5.5 Implement batch dispatcher (scheduled task) — dequeue N users every X seconds (configurable)
- [ ] 5.6 Implement token expiration handler — release slot when JWT expires without use
- [ ] 5.7 Add backpressure integration — pause dispatcher when Reservation module circuit breaker is open
- [ ] 5.8 Write integration tests for queue operations (join, status, token, batch dispatch)

## 6. Reservation Module (Anti-Overbooking)

- [ ] 6.1 Implement domain entities: Reservation, ReservationItem, TicketLock
- [ ] 6.2 Implement pessimistic stock verification with PostgreSQL `SELECT FOR UPDATE`
- [ ] 6.3 Implement Redis distributed lock with `SET NX EX 600` for exclusive seat/zone reservation
- [ ] 6.4 Implement per-customer ticket limit validation (configurable max, e.g., 3 per event)
- [ ] 6.5 Implement `POST /api/v1/reservations` endpoint — validate queue token, check stock, create lock, persist reservation
- [ ] 6.6 Implement `GET /api/v1/reservations/{id}` endpoint — return reservation details with TTL
- [ ] 6.7 Implement `DELETE /api/v1/reservations/{id}` endpoint — release Redis lock, update status, publish event
- [ ] 6.8 Configure Redis keyspace notifications for TTL expiration events
- [ ] 6.9 Implement reservation expiry listener — on Redis key expiration, publish `reservation-expired` to Redpanda, update PostgreSQL
- [ ] 6.10 Implement `payment-confirmed` event consumer — convert reservation to confirmed sale, remove Redis TTL
- [ ] 6.11 Implement `payment-failed` event consumer — cancel reservation, release lock, increment stock
- [ ] 6.12 Publish `reservation-created` and `reservation-cancelled` events to Redpanda
- [ ] 6.13 Write integration tests verifying zero overbooking under concurrent reservation attempts

## 7. Payment Module

- [ ] 7.1 Implement domain entities: Payment, PaymentTransaction with idempotency key
- [ ] 7.2 Implement `POST /api/v1/payments` endpoint — create payment record, initiate checkout (stubbed provider)
- [ ] 7.3 Implement `GET /api/v1/payments/{id}` endpoint — return payment status and details
- [ ] 7.4 Implement `POST /api/v1/payments/{id}/confirm` webhook endpoint — confirm payment with idempotency check
- [ ] 7.5 Publish `payment-confirmed` event to Redpanda on successful confirmation
- [ ] 7.6 Publish `payment-failed` event to Redpanda on payment failure
- [ ] 7.7 Implement payment failure handler — update status, trigger reservation cancellation
- [ ] 7.8 Write integration tests for payment flow including idempotency and duplicate confirmation scenarios

## 8. Event Streaming (Redpanda)

- [ ] 8.1 Configure Spring Cloud Stream with Redpanda (Kafka-compatible) binder
- [ ] 8.2 Define event schemas: reservation-created, reservation-cancelled, reservation-expired, payment-confirmed, payment-failed
- [ ] 8.3 Implement event producers in Reservation and Payment modules
- [ ] 8.4 Implement event consumers with idempotent processing and dead letter queue configuration
- [ ] 8.5 Configure Redpanda topics, partitions, and replication factor in Helm values

## 9. API Gateway

- [ ] 9.1 Create Spring Cloud Gateway application with route configuration for all modules
- [ ] 9.2 Configure JWT validation with Keycloak JWKS endpoint using spring-boot-starter-oauth2-resource-server
- [ ] 9.3 Implement rate limiting filter with Resilience4j (configurable per-client limits)
- [ ] 9.4 Configure role-based access control filters (ADMIN for catalog mutations, USER for purchases)
- [ ] 9.5 Add public route configuration for catalog queries (no auth required)
- [ ] 9.6 Configure CORS, request logging, and error handling filters
- [ ] 9.7 Write integration tests for routing, auth, and rate limiting

## 10. Security (Keycloak)

- [ ] 10.1 Deploy Keycloak on K3s via Helm chart with PostgreSQL backend
- [ ] 10.2 Configure Keycloak realm, clients (monolith, gateway), and roles (USER, ADMIN)
- [ ] 10.3 Configure Spring Boot resource server in monolith to validate Keycloak JWTs
- [ ] 10.4 Implement queue access token issuance (JWT with event ID, user ID, short TTL)

## 11. Resilience (Resilience4j)

- [ ] 11.1 Configure circuit breaker instances per module (Catalog, Queue, Reservation, Payment)
- [ ] 11.2 Configure rate limiter for reservation creation endpoint
- [ ] 11.3 Configure retry with exponential backoff for external calls (payment provider, Keycloak)
- [ ] 11.4 Configure timeout for database operations and Redis calls
- [ ] 11.5 Configure bulkhead to isolate thread pools per module
- [ ] 11.6 Add fallback methods for circuit breaker open scenarios

## 12. Observability

- [ ] 12.1 Configure Logback with JSON structured logging including MDC (trace ID, span ID, user ID)
- [ ] 12.2 Configure Micrometer with Prometheus registry and custom business metrics (reservations, payments, queue depth)
- [ ] 12.3 Add OpenTelemetry Java Agent configuration for distributed tracing with W3C Trace Context propagation
- [ ] 12.4 Deploy Grafana stack via Helm (Prometheus, Loki, Tempo, Grafana)
- [ ] 12.5 Create Grafana dashboard: "Ticket Monster Overview" (request rate, error rate, latency, queue depth, reservation/payment throughput)
- [ ] 12.6 Create Grafana dashboard: "Infrastructure" (Redis, PostgreSQL, MongoDB, Redpanda, K3s pod metrics)
- [ ] 12.7 Configure Loki log scraping for application pods
- [ ] 12.8 Configure Tempo trace ingestion with OpenTelemetry collector

## 13. Load Testing

- [ ] 13.1 Create k6 script for mass sale simulation (queue join → token → reservation → payment)
- [ ] 13.2 Create k6 script for catalog query load (events list, search, availability)
- [ ] 13.3 Create k6 script for overbooking prevention verification (excess concurrent reservations)
- [ ] 13.4 Configure k6 thresholds for latency (p95 < 500ms), error rate (< 1%), and zero overbooking
- [ ] 13.5 Integrate k6 execution into `provision-services.sh`

## 14. Documentation

- [ ] 14.1 Create README.md with project overview, architecture description, and quickstart guide
- [ ] 14.2 Add Mermaid diagram: Architecture general (flowchart — users, monolith, infra, communication)
- [ ] 14.3 Add Mermaid diagram: DDD Context Map (flowchart — bounded contexts and sync/async relationships)
- [ ] 14.4 Add Mermaid diagram: Purchase flow (sequenceDiagram — User → Queue → Reservation → Payment)
- [ ] 14.5 Add Mermaid diagram: Anti-overbooking flow (sequenceDiagram — Redis lock + PostgreSQL verification)
- [ ] 14.6 Add Mermaid diagram: Virtual queue (flowchart — dispatcher, batches, tokens, expiration)
- [ ] 14.7 Add Mermaid diagram: Deployment (flowchart — K3s, namespaces, pods, ingress)
- [ ] 14.8 Document evaluation questionnaire responses (overbooking, resilience, concurrency, mass arrival, CAP theorem)
- [ ] 14.9 Document monolith-to-microservices evolution strategy with extraction criteria
