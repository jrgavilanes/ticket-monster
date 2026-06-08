## ADDED Requirements

### Requirement: CORS headers for cross-origin requests
The API Gateway SHALL include CORS headers in responses to cross-origin requests from configured origins.

#### Scenario: Request from allowed origin
- **WHEN** a request is sent from origin `http://localhost:3000`
- **THEN** the response SHALL include the `Access-Control-Allow-Origin: http://localhost:3000` header

#### Scenario: Preflight OPTIONS request
- **WHEN** an OPTIONS preflight request is sent from an allowed origin
- **THEN** the response SHALL include `Access-Control-Allow-Methods`, `Access-Control-Allow-Headers`, and `Access-Control-Allow-Credentials` headers

#### Scenario: Request from disallowed origin
- **WHEN** a request is sent from a non-configured origin
- **THEN** the response SHALL NOT include `Access-Control-Allow-Origin`
