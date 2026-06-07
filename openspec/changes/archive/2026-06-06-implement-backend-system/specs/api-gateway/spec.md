## ADDED Requirements

### Requirement: Single entry point routing
The API Gateway SHALL serve as the single entry point for all client requests, routing them to the appropriate module within the modular monolith based on URL path patterns.

#### Scenario: Route to Catalog module
- **WHEN** a client sends a request to `/graphql`
- **THEN** the gateway SHALL route the request to the Catalog Module's GraphQL endpoint

#### Scenario: Route to Queue module
- **WHEN** a client sends a request to `/api/v1/queue/**`
- **THEN** the gateway SHALL route the request to the Virtual Queue Module

#### Scenario: Route to Reservation module
- **WHEN** a client sends a request to `/api/v1/reservations/**`
- **THEN** the gateway SHALL route the request to the Reservation Module

#### Scenario: Route to Payment module
- **WHEN** a client sends a request to `/api/v1/payments/**`
- **THEN** the gateway SHALL route the request to the Payment Module

### Requirement: JWT validation at gateway
The API Gateway SHALL validate JWT tokens issued by Keycloak before forwarding requests to backend modules. Modules SHALL trust the gateway's validation.

#### Scenario: Valid JWT
- **WHEN** a request arrives with a valid, non-expired JWT signed by Keycloak
- **THEN** the gateway SHALL validate the token, extract user claims, forward the request with the user ID in a header, and allow the request to proceed

#### Scenario: Missing JWT on protected endpoint
- **WHEN** a request arrives without an Authorization header on a protected endpoint
- **THEN** the gateway SHALL reject the request with a 401 Unauthorized response

#### Scenario: Expired or invalid JWT
- **WHEN** a request arrives with an expired or invalid JWT
- **THEN** the gateway SHALL reject the request with a 401 Unauthorized response

### Requirement: Rate limiting
The API Gateway SHALL enforce rate limiting using Resilience4j to protect backend modules from traffic spikes.

#### Scenario: Request within rate limit
- **WHEN** a client sends requests within the configured rate limit (e.g., 100 requests per minute per user)
- **THEN** the gateway SHALL forward all requests normally

#### Scenario: Request exceeds rate limit
- **WHEN** a client exceeds the configured rate limit
- **THEN** the gateway SHALL reject excess requests with a 429 Too Many Requests response including a `Retry-After` header

### Requirement: Circuit breaker at gateway
The API Gateway SHALL implement a Resilience4j circuit breaker to prevent cascade failures when backend modules are degraded.

#### Scenario: Backend module healthy
- **WHEN** the backend module responds successfully within timeout
- **THEN** the circuit breaker SHALL remain closed and forward requests normally

#### Scenario: Backend module failing
- **WHEN** the backend module returns errors exceeding the circuit breaker threshold (e.g., 50% failure rate over 10 requests)
- **THEN** the circuit breaker SHALL open and immediately reject requests with a 503 Service Unavailable response without forwarding to the backend

#### Scenario: Circuit breaker half-open recovery
- **WHEN** the circuit breaker is open and the wait duration has elapsed
- **THEN** the circuit breaker SHALL transition to half-open, allow a probe request through, and close if the probe succeeds

### Requirement: CORS configuration
The API Gateway SHALL configure CORS policies to allow requests from authorized frontend origins.

#### Scenario: Request from allowed origin
- **WHEN** a browser sends a cross-origin request from an allowed origin
- **THEN** the gateway SHALL include appropriate CORS headers in the response

#### Scenario: Preflight request
- **WHEN** a browser sends an OPTIONS preflight request
- **THEN** the gateway SHALL respond with allowed methods, headers, and origins
