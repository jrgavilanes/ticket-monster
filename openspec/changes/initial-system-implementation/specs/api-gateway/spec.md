## ADDED Requirements

### Requirement: JWT authentication
The API Gateway SHALL validate JWT tokens issued by Keycloak on every incoming request and reject requests with invalid or missing tokens for protected endpoints.

#### Scenario: Valid JWT token
- **WHEN** a client sends a request with a valid Bearer JWT token in the Authorization header
- **THEN** the API Gateway SHALL validate the token signature, expiry, and issuer against Keycloak's JWKS endpoint, and forward the request to the appropriate downstream module with the user identity in headers

#### Scenario: Missing token on protected endpoint
- **WHEN** a client sends a request to a protected endpoint without an Authorization header
- **THEN** the API Gateway SHALL reject the request with HTTP 401 Unauthorized

#### Scenario: Expired token
- **WHEN** a client sends a request with an expired JWT token
- **THEN** the API Gateway SHALL reject the request with HTTP 401 Unauthorized

#### Scenario: Public endpoint access
- **WHEN** a client sends a request to a public endpoint (e.g., event catalog queries) without a token
- **THEN** the API Gateway SHALL forward the request without requiring authentication

### Requirement: Rate limiting
The API Gateway SHALL enforce rate limiting per client to protect downstream modules from traffic spikes.

#### Scenario: Within rate limit
- **WHEN** a client sends requests within the configured rate limit (e.g., 100 requests per minute)
- **THEN** the API Gateway SHALL forward all requests to downstream modules

#### Scenario: Rate limit exceeded
- **WHEN** a client exceeds the configured rate limit
- **THEN** the API Gateway SHALL reject excess requests with HTTP 429 Too Many Requests and include a `Retry-After` header

### Requirement: Request routing
The API Gateway SHALL route incoming requests to the appropriate module based on URL path patterns.

#### Scenario: Route to Catalog module
- **WHEN** a client sends a request to `/graphql` or `/api/v1/catalog/**`
- **THEN** the API Gateway SHALL route the request to the Catalog module

#### Scenario: Route to Queue module
- **WHEN** a client sends a request to `/api/v1/queue/**`
- **THEN** the API Gateway SHALL route the request to the Virtual Queue module

#### Scenario: Route to Reservation module
- **WHEN** a client sends a request to `/api/v1/reservations/**`
- **THEN** the API Gateway SHALL route the request to the Reservation module

#### Scenario: Route to Payment module
- **WHEN** a client sends a request to `/api/v1/payments/**`
- **THEN** the API Gateway SHALL route the request to the Payment module

### Requirement: Role-based access control
The API Gateway SHALL enforce role-based access control using JWT claims for ADMIN and USER roles.

#### Scenario: ADMIN accesses catalog management
- **WHEN** a user with ADMIN role sends a mutation request to the Catalog module
- **THEN** the API Gateway SHALL forward the request with the role claim available for downstream authorization

#### Scenario: USER attempting admin operation
- **WHEN** a user with USER role sends a request to an admin-only endpoint
- **THEN** the API Gateway SHALL reject the request with HTTP 403 Forbidden
