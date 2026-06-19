## ADDED Requirements

### Requirement: CORS via Traefik headers middleware
Traefik SHALL handle CORS configuration at the edge using the `ticket-monster-secure-headers` Middleware CRD and Traefik's built-in CORS support. Cross-origin requests from the configured frontend origin SHALL be allowed.

#### Scenario: Request from allowed origin
- **WHEN** a request is sent from the configured frontend origin
- **THEN** the response SHALL include appropriate CORS headers allowing the origin, methods (GET, POST, PUT, DELETE, OPTIONS), and credentials

#### Scenario: Preflight OPTIONS request
- **WHEN** an OPTIONS preflight request is sent from an allowed origin
- **THEN** the response SHALL include allowed methods, headers, and credentials headers

## REMOVED Requirements

### Requirement: CORS headers for cross-origin requests
**Reason**: CORS configuration is now handled by Traefik at the edge rather than the API Gateway. The gateway's `globalcors` configuration in `application.yml` is no longer deployed.
**Migration**: CORS is configured via Traefik middleware and headers. The `CorsTest.java` and gateway CORS configuration remain in the repo for reference.
