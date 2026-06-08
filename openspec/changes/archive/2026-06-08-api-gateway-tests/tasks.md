## 1. Test Infrastructure Setup

- [x] 1.1 Add WireMock and reactor-test dependencies to `build.gradle`
- [x] 1.2 Create base test class `GatewayTestBase` with `@SpringBootTest(webEnvironment = RANDOM_PORT)`, WireMock setup, and `WebTestClient`
- [x] 1.3 Configure `@TestPropertySource` overrides to disable Redis rate limiter and point to WireMock Keycloak JWKS endpoint

## 2. Routing Tests

- [x] 2.1 Implement `RoutingTest` with parameterized test covering all 4 routes: `/graphql`, `/api/v1/queue/**`, `/api/v1/reservations/**`, `/api/v1/payments/**`
- [x] 2.2 Implement test for unknown path returning 404
- [x] 2.3 Verify each route forwards to the correct WireMock backend stub

## 3. Security Tests

- [x] 3.1 Implement `SecurityTest` with mock JWKS endpoint via WireMock
- [x] 3.2 Implement test for public access to `/graphql` and `/actuator/health` without JWT
- [x] 3.3 Implement test for 401 on protected routes without JWT (queue, reservation, payment)
- [x] 3.4 Implement test for valid JWT accepted on protected route
- [x] 3.5 Implement test for invalid/expired JWT rejected with 401
- [x] 3.6 Implement `JwtConfigTest` unit test for role extraction from `realm_access` claim

## 4. Resilience / Circuit Breaker Tests

- [x] 4.1 Implement `CircuitBreakerTest` with WireMock returning 500 series errors
- [x] 4.2 Implement test that verifies 503 fallback response after threshold exceeded
- [x] 4.3 Implement test verifying fallback JSON body for each module (catalog, queue, reservation, payment)
- [x] 4.4 Implement test verifying connection timeout triggers circuit breaker open

## 5. CORS Tests

- [x] 5.1 Implement `CorsTest` for requests from allowed origin `http://localhost:3000`
- [x] 5.2 Implement test for preflight OPTIONS request CORS headers
- [x] 5.3 Implement test for disallowed origin not getting CORS headers

## 6. Rate Limiter Key Resolver Tests

- [x] 6.1 Implement `RateLimiterConfigTest` unit test for key resolution from `X-User-Id` header
- [x] 6.2 Implement test for fallback to remote IP when header absent
- [x] 6.3 Implement test for `anonymous` fallback when neither header nor remote address available

## 7. Build and Verify

- [x] 7.1 Run full gateway test suite and fix any failures
- [x] 7.2 Verify tests pass without external Keycloak, Redis, or backend monolith
