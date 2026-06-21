feat: fix e2e-purchase test and add synchronous confirmSale

Backend (PaymentService.java):
- Inject ReservationService into PaymentService
- Call confirmSale synchronously after payment confirmation
  to release reservation locks immediately instead of relying
  on async Kafka consumer

Backend (PaymentServiceTest.java):
- Add @Mock ReservationService to match new constructor

K6 test (e2e-purchase.js):
- Add Authorization header to payment confirm request (was 401)
- Support multi-user token pool via TOKENS_FILE env var
- Accept HTTP 409 as valid reservation response (lock contention
  is expected anti-overbooking behavior)
- Adjust http_req_failed threshold to 0.05

K6 harness (run-e2e-purchase.sh):
- Auto-create venue, artist, and event with fixed zone IDs
- Provision 50 Keycloak test users for distributed VU identity
- Clean Redis locks, queues, and PostgreSQL between runs
- Zone capacity set to 5000 to avoid stock exhaustion

Helm chart (ticketmonster):
- Add Traefik rate-limit middleware template (10000 req/s)
- Add secure-headers middleware template
- Add QUEUE_BATCH_SIZE and QUEUE_BATCH_INTERVAL_MS env vars

K6 test (reservation-contention.js):
- Support multi-user token pool via TOKENS_FILE (__VU % tokens.length)
- 1000 VUs competing for 10 tickets in zone-vip → PostgreSQL
  pessimistic lock (SELECT FOR UPDATE) is the contention bottleneck

K6 harness (run-reservation-contention.sh):
- Auto-create venue, artist, event with zone-vip capacity=10
- Provision 100 Keycloak test users for distributed VU identity
- Clean Redis locks and PostgreSQL between runs

Keycloak user cleanup:
  KEYCLOAK_URL="https://janrax.es/auth"
  MASTER_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=admin-cli" -d "username=admin" \
    -d "password=35bc14bca662a2e74654bd731a0058f3f642aa0b813617f039d730fc63736380" \
    -d "grant_type=password" | jq -r '.access_token')
  for i in $(seq 1 100); do
    USER_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/ticket-monster/users?username=k6user${i}" \
      -H "Authorization: Bearer $MASTER_TOKEN" | jq -r '.[0].id // empty')
    [ -n "$USER_ID" ] && curl -s -X DELETE "$KEYCLOAK_URL/admin/realms/ticket-monster/users/$USER_ID" \
      -H "Authorization: Bearer $MASTER_TOKEN"
  done

feat: add harness scripts and document queue/reservation/catalog load tests

K6 tests (catalog-read.js, queue-load.js, reservation-contention.js):
- Support multi-user token pool via TOKENS_FILE (__VU % tokens.length)
- Fix handleSummary in reservation-contention.js (iterations_completed
  → iterations.values.count)
- Add EVENT_ID env var to catalog-read.js for dynamic availability query

K6 harnesses (run-catalog-read.sh, run-queue-load.sh, run-reservation-contention.sh):
- Auto-create venue, artist, event and publish via GraphQL
- Provision Keycloak test users (50 queue, 100 reservation)
- Generate TOKENS_FILE json with all user JWT tokens
- Clean Redis locks/queues and PostgreSQL data between runs
- Configurable VUs, duration, zone capacity per test

README.md:
- Document all three tests with flow diagrams, results tables,
  scale limits, and 3-way stack comparison:
    Catalog/MongoDB:       200 VUs, p95 5.61s, 91 req/s
    Queue/Redis:           200 VUs, p95 676ms, 526 req/s
    Reservation/PG+Redis: 1000 VUs, p95 3.17s, ~250 req/s
- Document Traefik/kernel TCP bottleneck at 10K VUs (5 pods worse
  than 1 — Traefik pod crashed)
- Note that catalog-read is 8× slower than queue-load because
  MongoDB latency dominates; scaling pods does not help
- Note that virtual threads are enabled (spring.threads.virtual.enabled: true)
  confirming app thread pool is not the bottleneck
