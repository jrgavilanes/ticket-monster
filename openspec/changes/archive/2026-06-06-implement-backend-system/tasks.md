## 1. Project Setup and Dependencies

- [x] 1.1 Configure `backend/ticketmonster` as a Spring Modulith project: add `spring-modulith-starter-core` dependency, create module packages (`catalog`, `queue`, `reservation`, `payment`) with `package-info.java` boundaries
- [x] 1.2 Add Spring Boot starters to `ticketmonster`: `spring-boot-starter-web`, `spring-boot-starter-data-jpa`, `spring-boot-starter-data-mongodb`, `spring-boot-starter-graphql`, `spring-boot-starter-oauth2-resource-server`, `spring-boot-starter-actuator`, `spring-boot-starter-validation`
- [x] 1.3 Add infrastructure client dependencies to `ticketmonster`: Spring Kafka (for Redpanda), Lettuce (Redis via `spring-boot-starter-data-redis`), Resilience4j (`resilience4j-spring-boot3`), Micrometer Prometheus registry, OpenTelemetry API
- [x] 1.4 Configure `application.yml` for `ticketmonster`: datasource (PostgreSQL), MongoDB, Redis, Kafka/Redpanda bootstrap servers, OAuth2 resource server (Keycloak JWKS URI), actuator endpoints, Modulith event publication
- [x] 1.5 Add Spring Modulith architecture verification test that asserts module boundaries and detects illegal cross-module dependencies
- [x] 1.6 Configure `backend/api-gateway` with `spring-cloud-starter-gateway`, `spring-boot-starter-oauth2-resource-server`, `resilience4j-spring-cloud-gateway`, and `spring-boot-starter-actuator`

## 2. Infrastructure Provisioning Scripts

- [x] 2.1 Create `scripts/provision-k3s.sh`: SSH into remote Debian 12 VPS, install K3s (curl pipe to sh), install Helm and k9s, copy kubeconfig locally, make script idempotent (detect existing K3s installation)
- [x] 2.2 Create `scripts/provision-infra.sh`: deploy cert-manager with Let's Encrypt ClusterIssuer, deploy Redpanda (official Helm chart), Redis (Bitnami), PostgreSQL (Bitnami), MongoDB (Bitnami), Keycloak, and Grafana stack (Prometheus, Loki, Tempo, Grafana) — all via `helm upgrade --install`
- [x] 2.3 Add auto-generated secrets logic to `provision-infra.sh`: use `openssl rand -hex 32` for database passwords and API keys, store as K8s Secrets, skip if secrets already exist (idempotent)
- [x] 2.4 Create `scripts/provision-services.sh`: build Docker image for ticketmonster and api-gateway, deploy via `helm upgrade --install`, configure ingress, wait for rollout, execute k6 tests at the end

## 3. Catalog Module (GraphQL + MongoDB)

- [x] 3.1 Define MongoDB document models: `Venue` (name, location, capacity, layout), `Event` (name, type, date, venueId, artistIds, zones), `Artist` (name, genre, bio), `Zone` (name, capacity, price, eventId)
- [x] 3.2 Create Spring Data MongoDB repositories for Venue, Event, Artist, and Zone with custom query methods (search by name regex, find by date range, find by venue)
- [x] 3.3 Implement GraphQL schema (`schema.graphqls`): define types for Event, Venue, Artist, Zone, Availability; define queries `events`, `event(id)`, `searchEvents(query, filters)`, `availability(eventId)`; define admin mutations `createEvent`, `updateEvent`, `deleteEvent`
- [x] 3.4 Implement GraphQL resolvers (controllers) for all queries: `events` with pagination, `event(id)` with nested venue/artist/zone resolution, `searchEvents` with text search and filter arguments
- [x] 3.5 Implement `availability(eventId)` resolver that queries the Reservation Module's PostgreSQL stock data (not MongoDB) to return real-time zone availability
- [x] 3.6 Implement admin mutations with `@PreAuthorize("hasRole('ADMIN')")` for creating, updating, and deleting catalog entities
- [x] 3.7 Add MongoDB text indexes on event name, artist name, and venue name for efficient search queries

## 4. Virtual Queue Module (Redis FIFO)

- [x] 4.1 Implement Redis FIFO queue service: `join(eventId, userId)` using `LPUSH` to add users, `getPosition(eventId, userId)` using `LPOS` to find position, `dequeue(eventId, count)` using `RPOP` for batch release
- [x] 4.2 Implement REST controller: `POST /api/v1/queue/{eventId}/join` (enqueue user, return ticket ID and position), `GET /api/v1/queue/{eventId}/status` (return position or TURN_READY), `GET /api/v1/queue/{eventId}/token` (issue JWT if turn arrived)
- [x] 4.3 Implement batch dispatcher as a `@Scheduled` task: configurable batch size (default 500) and interval (default 2s), dequeue N users per tick, mark them as TURN_READY in a Redis hash
- [x] 4.4 Implement queue access token issuance: generate a short-lived JWT (default 5 min TTL) with claims for userId, eventId, and queueTicketId; validate this token in the Reservation Module
- [x] 4.5 Handle token expiration: when a queue access token expires without a reservation being made, release the slot and allow the dispatcher to advance the next batch
- [x] 4.6 Add duplicate join protection: check if user is already in queue before enqueuing (use a Redis SET to track active user IDs per event)

## 5. Reservation Module (Anti-Overbooking Core)

- [x] 5.1 Define PostgreSQL entities and schema: `Reservation` (id, userId, eventId, status, expiresAt, createdAt), `ReservationItem` (id, reservationId, zoneId, seatId, quantity), `ZoneStock` (eventId, zoneId, totalCapacity, availableCount) with Flyway migration scripts
- [x] 5.2 Implement distributed lock service using Redis: `acquireLock(eventId, zoneId, userId, ttlSeconds)` using `SET reservation:{eventId}:{zoneId}:{userId} {userId} EX {ttl} NX`, `releaseLock(eventId, zoneId, userId)` using `DEL`
- [x] 5.3 Implement reservation creation: `POST /api/v1/reservations` — validate queue access token, check per-customer ticket limit (configurable, default 3), acquire Redis lock (`SET NX`), verify and decrement stock in PostgreSQL (`SELECT FOR UPDATE` + `UPDATE ... WHERE available >= requested`), publish `reservation-created` event to Redpanda
- [x] 5.4 Implement reservation query: `GET /api/v1/reservations/{id}` — return reservation details with status (ACTIVE, EXPIRED, CANCELLED, SOLD), enforce ownership check (403 if not owner)
- [x] 5.5 Implement reservation cancellation: `DELETE /api/v1/reservations/{id}` — release Redis lock, increment stock in PostgreSQL, publish `reservation-cancelled` event to Redpanda
- [x] 5.6 Implement TTL expiration handler: subscribe to Redis keyspace notifications for expired reservation keys, publish `reservation-expired` event to Redpanda, increment stock in PostgreSQL
- [x] 5.7 Implement fallback expiration sweep: `@Scheduled` job (every 60s) that queries PostgreSQL for reservations where `expires_at < NOW() AND status = 'ACTIVE'`, processes their expiration, and increments stock
- [x] 5.8 Implement `payment-confirmed` event consumer: listen to Redpanda topic, update reservation status to `SOLD`, remove Redis TTL lock (or delete it), persist sale record
- [x] 5.9 Handle race condition in `payment-confirmed` for expired reservation: log inconsistency, publish `payment-refund-required` event, do NOT create sale

## 6. Payment Module (PostgreSQL + Idempotency)

- [x] 6.1 Define PostgreSQL entities and schema: `Payment` (id, reservationId, userId, amount, status, idempotencyKey, createdAt, confirmedAt), `PaymentAudit` (id, paymentId, previousStatus, newStatus, actor, timestamp) with Flyway migration scripts
- [x] 6.2 Implement payment initiation: `POST /api/v1/payments` — validate reservation is ACTIVE, create payment record with status PENDING and generated idempotency key, return payment ID
- [x] 6.3 Implement payment query: `GET /api/v1/payments/{id}` — return payment details with current status, enforce ownership check (403 if not owner)
- [x] 6.4 Implement payment confirmation webhook: `POST /api/v1/payments/{id}/confirm` — check idempotency key (return existing if duplicate), update status to CONFIRMED, record audit entry, publish `payment-confirmed` event to Redpanda
- [x] 6.5 Implement idempotency: store idempotency keys in a unique-indexed column, on duplicate insert return existing record without reprocessing
- [x] 6.6 Implement audit trail: record every status transition (PENDING → CONFIRMED, PENDING → FAILED) in the `PaymentAudit` table with timestamp and actor

## 7. API Gateway (Spring Cloud Gateway)

- [x] 7.1 Configure route definitions in `application.yml`: `/graphql` → Catalog, `/api/v1/queue/**` → Queue, `/api/v1/reservations/**` → Reservation, `/api/v1/payments/**` → Payment (routes target the monolith's internal endpoints)
- [x] 7.2 Configure OAuth2 resource server: validate JWTs from Keycloak using JWKS URI, extract user claims, forward user ID in `X-User-Id` header to downstream modules
- [x] 7.3 Configure Resilience4j rate limiter: per-user rate limit (e.g., 100 req/min), return 429 with `Retry-After` header when exceeded
- [x] 7.4 Configure Resilience4j circuit breaker: per-route circuit breaker with 50% failure threshold, 10s wait duration, half-open probe with 3 permitted calls
- [x] 7.5 Configure CORS: allow specified frontend origins, handle OPTIONS preflight requests with appropriate headers
- [x] 7.6 Configure public routes: allow unauthenticated access to Catalog GraphQL queries (read-only) and health endpoints

## 8. Observability Stack

- [x] 8.1 Configure Logback JSON logging in `ticketmonster`: add `logstash-logback-encoder` dependency, configure JSON layout with traceId/spanId from MDC (OpenTelemetry), set log level per module
- [x] 8.2 Configure Micrometer metrics: enable Prometheus registry in Actuator, add custom counters for reservation state transitions (created, expired, confirmed, cancelled), add gauge for active reservations and queue depth
- [x] 8.3 Configure OpenTelemetry Java Agent: add agent to Dockerfile (`-javaagent:/otel/opentelemetry-javaagent.jar`), set service name, configure OTLP exporter to Tempo endpoint
- [x] 8.4 Create Grafana dashboard JSON for system overview: request rate, error rate, latency percentiles (p50/p95/p99), active reservations gauge, queue depth gauge, pod count
- [x] 8.5 Create Grafana dashboard JSON for Reservation Module: reservation creation rate, expiration rate, Redis lock acquisition latency, PostgreSQL query latency, lock contention count
- [x] 8.6 Configure Loki Promtail/Alloy to scrape application logs from pods and ship to Loki
- [x] 8.7 Configure Grafana data sources (Prometheus, Loki, Tempo) and log-trace correlation via traceId

## 9. Helm Charts and Deployment

- [x] 9.1 Create `deploy/charts/ticketmonster/` Helm chart: `Chart.yaml`, `values.yaml`, templates for Deployment, Service, Ingress, ConfigMap, HPA, and `secretRef` for infrastructure credentials
- [x] 9.2 Create `deploy/charts/api-gateway/` Helm chart: Deployment, Service, Ingress with TLS (cert-manager annotation), HPA
- [x] 9.3 Create `deploy/infra/` value overrides for each infrastructure component: `cert-manager/values.yaml`, `redpanda/values.yaml`, `redis/values.yaml`, `postgresql/values.yaml`, `mongodb/values.yaml`, `keycloak/values.yaml`, `observability/values.yaml`
- [x] 9.4 Configure HPA in ticketmonster chart: target CPU 70%, memory 80%, min replicas 2, max replicas 10
- [x] 9.5 Create Dockerfiles for `ticketmonster` (multi-stage build with OpenTelemetry agent) and `api-gateway`

## 10. Load Testing (k6)

- [x] 10.1 Create `deploy/tests/k6/queue-load.js`: simulate 10,000 virtual users joining the queue concurrently, assert all receive valid positions with 0 errors
- [x] 10.2 Create `deploy/tests/k6/reservation-contention.js`: simulate 1,000 concurrent reservation requests for a zone with 100 tickets, assert exactly 100 succeed and 900 receive 409, verify zero overbooking
- [x] 10.3 Create `deploy/tests/k6/e2e-purchase.js`: simulate 500 concurrent users through full flow (join queue → wait for token → reserve → pay), assert all 500 complete successfully (with sufficient stock)
- [x] 10.4 Create `deploy/tests/k6/catalog-read.js`: simulate 5,000 concurrent GraphQL queries for events and availability, assert p95 latency < 200ms

## 11. Documentation

- [x] 11.1 Create README.md with project overview, architecture description, and quick-start guide
- [x] 11.2 Add Mermaid `flowchart` diagram: high-level architecture (users → API Gateway → monolith modules → databases → Redpanda)
- [x] 11.3 Add Mermaid `flowchart` diagram: DDD Context Map showing bounded contexts and their relationships (sync/async)
- [x] 11.4 Add Mermaid `sequenceDiagram`: full purchase flow (User → Gateway → Queue → Reservation → Redpanda → Payment → Reservation)
- [x] 11.5 Add Mermaid `sequenceDiagram`: anti-overbooking flow (Redis lock acquisition → PostgreSQL SELECT FOR UPDATE → TTL expiration → stock release)
- [x] 11.6 Add Mermaid `flowchart`: virtual queue dispatcher (batch release, token issuance, expiration)
- [x] 11.7 Add Mermaid `flowchart`: deployment architecture (K3s cluster, namespaces, pods, ingress, services)
- [x] 11.8 Document CAP theorem analysis per module (Reservation=CP, Payment=CP, Catalog=AP, Queue=AP, Redpanda=CP)
- [x] 11.9 Document monolith-to-microservices evolution strategy and extraction criteria

## 12. Docker Compose Local Development

- [x] 12.1 Create `docker-compose.yml` at project root with infrastructure services: PostgreSQL (port 5432), MongoDB (port 27017), Redis (port 6379), Redpanda with console UI (port 8081), Keycloak (port 8080)
- [x] 12.2 Add observability stack to `docker-compose.yml`: Prometheus (port 9090), Loki (port 3100), Tempo (port 3200), Grafana (port 3000) with pre-configured data sources and dashboard provisioning
- [x] 12.3 Add application services to `docker-compose.yml`: `ticketmonster` monolith and `api-gateway` with `depends_on` health check conditions, environment variables pointing to infrastructure services, and Spring Boot DevTools for hot-reload
- [x] 12.4 Configure named volumes for data persistence: `postgres-data`, `mongodb-data`, `redis-data`, `keycloak-data`, `grafana-data`
- [x] 12.5 Create Keycloak realm export file `docker/keycloak/realm-export.json` with `ticket-monster` realm: clients (`ticket-monster-app`, `api-gateway`), roles (`USER`, `ADMIN`), test users (`admin/admin` with ADMIN, `user/user` with USER)
- [x] 12.6 Configure Keycloak container to auto-import realm on startup via `--import-realm` flag and volume-mounted realm file
- [x] 12.7 Create Grafana provisioning configs: `docker/grafana/provisioning/datasources.yml` (Prometheus, Loki, Tempo) and `docker/grafana/provisioning/dashboards.yml` with dashboard JSON imports
- [x] 12.8 Create Redpanda initialization script `docker/redpanda/init-topics.sh` that creates required Kafka topics: `reservation-created`, `reservation-cancelled`, `reservation-expired`, `payment-confirmed`, `payment-refund-required`
- [x] 12.9 Add health checks to all services in `docker-compose.yml`: PostgreSQL (`pg_isready`), MongoDB (`mongosh --eval`), Redis (`redis-cli ping`), Redpanda (`rpk cluster health`), Keycloak (HTTP health endpoint)
- [x] 12.10 Create `.env.example` file documenting all configurable variables: database passwords, service ports, Keycloak admin credentials, JWT secret, Redpanda bootstrap servers
- [x] 12.11 Create `docker/prometheus/prometheus.yml` with scrape configs for ticketmonster and api-gateway Actuator metrics endpoints
- [x] 12.12 Create `docker/loki/loki-config.yml` with local storage configuration for log aggregation
- [x] 12.13 Add Docker Compose profiles: `infra` (infrastructure only), `app` (full stack), `dev` (infrastructure + app with debug ports exposed)
- [x] 12.14 Document local development workflow in README: how to start infra only and run app from IDE, how to start full stack, how to reset data with `docker compose down -v`
