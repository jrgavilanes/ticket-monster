## ADDED Requirements

### Requirement: Gateway behavior is documented through automated tests
The API Gateway SHALL have an automated test suite that validates all gateway capabilities and serves as executable documentation of gateway behavior.

#### Scenario: Route routing tests exist
- **WHEN** the test suite runs
- **THEN** there SHALL be tests verifying each configured route forwards requests to the correct backend path

#### Scenario: Security tests exist
- **WHEN** the test suite runs
- **THEN** there SHALL be tests verifying public path access, protected path enforcement, valid JWT acceptance, and invalid JWT rejection

#### Scenario: Circuit breaker tests exist
- **WHEN** the test suite runs
- **THEN** there SHALL be tests verifying circuit breaker opening on backend failures and fallback response structure

#### Scenario: CORS tests exist
- **WHEN** the test suite runs
- **THEN** there SHALL be tests verifying CORS headers on cross-origin and preflight requests

#### Scenario: Rate limiter key resolver tests exist
- **WHEN** the test suite runs
- **THEN** there SHALL be tests verifying the key resolver returns the correct key from X-User-Id header, remote IP, and anonymous fallback

### Requirement: Tests use WireMock for backend simulation
The gateway test suite SHALL use WireMock to simulate backend module responses without requiring the full monolith to be running.

#### Scenario: WireMock stub for backend success
- **WHEN** the test sends a request that the gateway routes to a backend module
- **THEN** WireMock SHALL return a configurable success response (200 OK)

#### Scenario: WireMock stub for backend failure
- **WHEN** testing circuit breaker behavior
- **THEN** WireMock SHALL return 500 errors for the configured number of requests

### Requirement: Tests do not require external infrastructure
The gateway test suite SHALL run without external dependencies (no Keycloak, Redis, or backend monolith).

#### Scenario: JWT validation uses mock JWKS
- **WHEN** the test suite runs with security tests
- **THEN** the Keycloak issuer URI SHALL be overridden to a WireMock endpoint serving a mock JWKS response

#### Scenario: Rate limiter disabled in tests
- **WHEN** the test suite runs
- **THEN** the rate limiter SHALL be disabled to avoid Redis dependency
