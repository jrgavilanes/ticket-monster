## Why

The API Gateway is a critical infrastructure component that handles routing, authentication, rate limiting, and circuit breaking for all client requests. Despite its importance, the only test is a context-loads smoke test. Without proper tests, regressions in gateway behavior (routing, security, resilience) can silently break the entire system. We need tests that validate the gateway's core responsibilities and serve as living documentation of its behavior.

## What Changes

- Add unit and integration tests for the API Gateway covering:
  - Route configuration: each route (`/graphql`, `/api/v1/queue/**`, `/api/v1/reservations/**`, `/api/v1/payments/**`) resolves correctly
  - Security: public vs. protected path enforcement with JWT validation
  - Circuit breaker: fallback behavior when backend modules are unavailable
  - CORS headers on cross-origin requests
  - Rate limiter key resolution (user ID header or remote IP fallback)
  - JWT role extraction from Keycloak `realm_access` claims
  - Health and metrics actuator endpoints
- Add test infrastructure with WireMock to simulate backend module responses and Keycloak JWK signing
- Update the existing spec to add testing-specific requirements

## Capabilities

### New Capabilities
- `gateway-routing`: Route path matching and forwarding behavior
- `gateway-security`: JWT validation and public/protected path enforcement
- `gateway-resilience`: Circuit breaker and fallback behavior
- `gateway-rate-limiting`: Rate limiter key resolution and configuration
- `gateway-cors`: CORS header generation for cross-origin requests

### Modified Capabilities

- `api-gateway`: Add testing requirements and acceptance criteria for gateway behavior

## Impact

- `backend/api-gateway/`: New test files, no production code changes expected
- `backend/api-gateway/build.gradle`: Add test dependencies (WireMock)
- Docs: Gateway behavior will be documented through tests (executable specifications)
- CI: Gateway test suite will run as part of the build pipeline
