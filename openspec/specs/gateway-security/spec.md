## ADDED Requirements

### Requirement: JWT authentication via Spring Security in-monolith
The monolith SHALL validate JWT tokens issued by Keycloak using Spring Security's OAuth2 Resource Server configuration. The monolith SHALL have the same public/protected path enforcement as the former gateway: `/graphql` and `/actuator/**` are public; `/api/v1/queue/**`, `/api/v1/reservations/**`, `/api/v1/payments/**` require authentication.

#### Scenario: Public GraphQL endpoint allows unauthenticated access
- **WHEN** a request without an Authorization header is sent to `/graphql`
- **THEN** the monolith SHALL process the request

#### Scenario: Protected endpoints require valid JWT
- **WHEN** a request without a valid JWT is sent to `/api/v1/queue/event1/join`
- **THEN** the monolith SHALL reject the request with 401 Unauthorized
