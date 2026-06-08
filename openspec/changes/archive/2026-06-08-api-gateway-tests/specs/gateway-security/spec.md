## ADDED Requirements

### Requirement: Gateway enforces JWT authentication on protected paths
The API Gateway SHALL require valid JWT tokens on protected routes and allow public access to configured paths.

#### Scenario: Public GraphQL endpoint allows unauthenticated access
- **WHEN** a request without an Authorization header is sent to `/graphql`
- **THEN** the gateway SHALL forward the request to the backend

#### Scenario: Public health endpoint allows unauthenticated access
- **WHEN** a request without an Authorization header is sent to `/actuator/health`
- **THEN** the gateway SHALL return a 200 OK response

#### Scenario: Protected queue endpoint requires authentication
- **WHEN** a request without an Authorization header is sent to `/api/v1/queue/event1/join`
- **THEN** the gateway SHALL reject the request with a 401 Unauthorized response

#### Scenario: Protected reservation endpoint requires authentication
- **WHEN** a request without an Authorization header is sent to `/api/v1/reservations`
- **THEN** the gateway SHALL reject the request with a 401 Unauthorized response

#### Scenario: Protected payment endpoint requires authentication
- **WHEN** a request without an Authorization header is sent to `/api/v1/payments`
- **THEN** the gateway SHALL reject the request with a 401 Unauthorized response

#### Scenario: Valid JWT is accepted on protected endpoint
- **WHEN** a request with a valid JWT is sent to a protected endpoint
- **THEN** the gateway SHALL forward the request to the backend

#### Scenario: Invalid JWT is rejected
- **WHEN** a request with an invalid or expired JWT is sent to a protected endpoint
- **THEN** the gateway SHALL reject the request with a 401 Unauthorized response

### Requirement: JWT role extraction from Keycloak realm_access
The API Gateway SHALL extract roles from the Keycloak `realm_access` claim and map them to Spring Security granted authorities with `ROLE_` prefix.

#### Scenario: Extract admin role
- **WHEN** a JWT contains a `realm_access` claim with roles `["admin", "user"]`
- **THEN** the gateway SHALL create authorities `ROLE_admin` and `ROLE_user`

#### Scenario: No realm_access claim
- **WHEN** a JWT does not contain a `realm_access` claim
- **THEN** the gateway SHALL return an empty set of authorities
