## ADDED Requirements

### Requirement: API Gateway is deprecated
The API Gateway SHALL be removed from the production deployment. Traefik (included in K3s) SHALL handle edge concerns (rate limiting, CORS, security headers) while the monolith handles auth via Spring Security and circuit breaking via Resilience4j.

#### Scenario: Gateway not deployed in production
- **WHEN** `deploy.sh` is executed
- **THEN** no API Gateway pod SHALL be created

#### Scenario: Gateway code preserved for reference
- **WHEN** a developer needs to understand or reactivate the gateway
- **THEN** the source code SHALL be available at `backend/api-gateway/` and the Helm chart SHALL be available at `deploy/charts/archive/api-gateway/`

## REMOVED Requirements

### Requirement: Single entry point routing
**Reason**: In a single-monolith architecture, there are no multiple backend services to route between. The monolith handles all requests directly.
**Migration**: Requests go directly to the monolith via Traefik Ingress at `janrax.es`.

### Requirement: JWT validation at gateway
**Reason**: JWT validation is handled by Spring Security in the monolith (`oauth2ResourceServer.jwt()`), which was already doing validation. The gateway's validation was redundant.
**Migration**: No change needed — the monolith already performs JWT validation.

### Requirement: Rate limiting
**Reason**: Rate limiting is now handled by Traefik Middleware `RateLimit` at the edge.
**Migration**: Rate limiting is configured via Traefik CRD at `deploy/k3s/middlewares/rate-limit.yaml` with the same values (100 req/s, 200 burst).

### Requirement: Circuit breaker at gateway
**Reason**: Circuit breakers are now handled by Resilience4j in the monolith for downstream dependencies (Redis, PostgreSQL, MongoDB, Redpanda). Edge circuit breaking is handled by Traefik if needed.
**Migration**: Resilience4j is already a dependency of the monolith. No additional configuration needed.

### Requirement: CORS configuration
**Reason**: CORS is now handled by Traefik headers middleware at the edge.
**Migration**: CORS headers are configured via Traefik CRD at `deploy/k3s/middlewares/secure-headers.yaml`.

### Requirement: Gateway behavior is documented through automated tests
**Reason**: The gateway test suite validated gateway-specific behavior (routing, circuit breakers). With the gateway deprecated, these tests are retained for historical reference only.
**Migration**: The monolith already has its own test suite for business logic. Gateway tests remain in the repo for reference.

### Requirement: Tests use WireMock for backend simulation
**Reason**: Deprecated with the gateway. The monolith has its own integration tests.
**Migration**: No replacement needed — the monolith's own tests cover integration scenarios.

### Requirement: Tests do not require external infrastructure
**Reason**: Deprecated with the gateway test suite.
**Migration**: N/A
