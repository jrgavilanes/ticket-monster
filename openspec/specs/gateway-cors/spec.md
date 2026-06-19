## ADDED Requirements

### Requirement: CORS via Traefik headers middleware
Traefik SHALL handle CORS configuration at the edge using the `ticket-monster-secure-headers` Middleware CRD and Traefik's built-in CORS support. Cross-origin requests from the configured frontend origin SHALL be allowed.

#### Scenario: Request from allowed origin
- **WHEN** a request is sent from the configured frontend origin
- **THEN** the response SHALL include appropriate CORS headers allowing the origin, methods (GET, POST, PUT, DELETE, OPTIONS), and credentials

#### Scenario: Preflight OPTIONS request
- **WHEN** an OPTIONS preflight request is sent from an allowed origin
- **THEN** the response SHALL include allowed methods, headers, and credentials headers
