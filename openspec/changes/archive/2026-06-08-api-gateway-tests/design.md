## Context

The API Gateway is a Spring Cloud Gateway application that routes requests to a modular monolith backend. Currently, the only test is a context-loads test (`ApiGatewayApplicationTests.java`). The gateway handles routing, JWT validation, rate limiting, circuit breaking, and CORS — all of which lack test coverage.

The test environment uses JUnit 5 + Spring WebFlux test (`WebTestClient`). No WireMock or testcontainers are currently used in the gateway module.

## Goals / Non-Goals

**Goals:**
- Add comprehensive tests that validate every gateway capability
- Use WireMock to simulate backend module HTTP responses without starting the full monolith
- Use Spring Cloud Gateway test utilities (`@AutoConfigureWebTestClient`) for route-level tests
- Validate JSON Web Key Set (JWKS) integration with a mock Keycloak JWKS endpoint
- Ensure tests run in isolation without external Redis, Keycloak, or backend module dependencies

**Non-Goals:**
- End-to-end tests that start the full monolith (existing `FullFlowIntegrationTest` covers this)
- Performance or load tests (covered by k6 scripts in `deploy/tests/k6/`)
- Changes to production gateway code
- Adding Testcontainers for Redis (use `@MockBean` or in-memory alternatives)

## Decisions

1. **WireMock over Testcontainers for backend simulation**
   - WireMock is lightweight and fast, perfect for simulating 4 backend modules returning success/failure/timeout responses
   - Testcontainers would require Docker, adding complexity and runtime overhead

2. **`application.yml` overrides in `@TestPropertySource`**
   - Disable Redis rate limiter in tests (rate limiting is tested via unit tests of the key resolver)
   - Override Keycloak issuer URI to a WireMock endpoint serving a mock JWKS
   - This allows full security testing without a running Keycloak

3. **Parameterized tests for routing**
   - All routes follow the same pattern (path predicate -> CircuitBreaker filter -> forward to monolith)
   - A single parameterized test method covers all 4 routes, ensuring each path resolves correctly

4. **MockMvc-style testing with `WebTestClient`**
   - `@SpringBootTest(webEnvironment = RANDOM_PORT)` with `WebTestClient` bound to the running gateway
   - WireMock server bound to a dynamic port, injected via `MONOLITH_URI` property override

## Risks / Trade-offs

- **WireMock may diverge from real backend behavior** → Keep WireMock stubs simple (200/500/timeout) and rely on monolith integration tests for full flow
- **Rate limiting cannot be tested without Redis** → Unit test the `KeyResolver` bean; integration-test Redis-backed rate limiting is a future enhancement
- **JWT validation uses mock JWKS** → Tests verify token verification logic but not Keycloak-specific nuances; acceptance tests against real Keycloak are in the full flow integration test
