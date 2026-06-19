## ADDED Requirements

### Requirement: JWT authentication via Spring Security in-monolith
The monolith SHALL validate JWT tokens issued by Keycloak using Spring Security's OAuth2 Resource Server configuration. The monolith SHALL have the same public/protected path enforcement as the former gateway: `/graphql` and `/actuator/**` are public; `/api/v1/queue/**`, `/api/v1/reservations/**`, `/api/v1/payments/**` require authentication.

#### Scenario: Public GraphQL endpoint allows unauthenticated access
- **WHEN** a request without an Authorization header is sent to `/graphql`
- **THEN** the monolith SHALL process the request

#### Scenario: Protected endpoints require valid JWT
- **WHEN** a request without a valid JWT is sent to `/api/v1/queue/event1/join`
- **THEN** the monolith SHALL reject the request with 401 Unauthorized

## REMOVED Requirements

### Requirement: Gateway enforces JWT authentication on protected paths
**Reason**: The monolith already had Spring Security configured for JWT validation (`oauth2ResourceServer.jwt()`). The gateway's validation was redundant — every request was validated twice (gateway then monolith).
**Migration**: JWT validation is handled exclusively by the monolith's Spring Security. No configuration change is needed in the monolith — it already performs this validation.

### Requirement: JWT role extraction from Keycloak realm_access
**Reason**: JWT role extraction is handled by Spring Security in the monolith via the same `JwtGrantedAuthoritiesConverter` that was also configured in the gateway. Removing the gateway eliminates the duplicate role extraction.
**Migration**: The monolith's `JwtConfig` already handles role extraction from `realm_access` claims. The gateway's `JwtConfig.java` is kept in the repo for reference only.
