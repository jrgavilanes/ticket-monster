## ADDED Requirements

### Requirement: Rate limiting via Traefik RateLimit middleware
Traefik SHALL enforce rate limiting using the `ticket-monster-rate-limit` Middleware CRD, configured at 100 requests per second average with 200 burst capacity. Requests exceeding this limit SHALL receive HTTP 429.

#### Scenario: Request within rate limit
- **WHEN** a client sends requests within 100 requests per second average
- **THEN** Traefik SHALL forward all requests to the monolith

#### Scenario: Request exceeds rate limit
- **WHEN** a client exceeds 100 requests per second average
- **THEN** Traefik SHALL reject excess requests with HTTP 429 Too Many Requests

## REMOVED Requirements

### Requirement: Rate limiter key resolves from X-User-Id header
**Reason**: The API Gateway's Redis-based rate limiter with key resolution is replaced by Traefik's built-in RateLimit middleware. Traefik uses the client's remote address for rate limiting by default, which is sufficient for edge protection without requiring Redis.
**Migration**: Rate limiting is configured via Traefik Middleware CRD at `deploy/k3s/middlewares/rate-limit.yaml`. The `RateLimiterConfig.java` and its tests remain in the repo for reference but are not deployed.
