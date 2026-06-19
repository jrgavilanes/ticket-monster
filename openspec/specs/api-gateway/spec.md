## ADDED Requirements

### Requirement: API Gateway is deprecated
The API Gateway SHALL be removed from the production deployment. Traefik (included in K3s) SHALL handle edge concerns (rate limiting, CORS, security headers) while the monolith handles auth via Spring Security and circuit breaking via Resilience4j.

#### Scenario: Gateway not deployed in production
- **WHEN** `deploy.sh` is executed
- **THEN** no API Gateway pod SHALL be created

#### Scenario: Gateway code preserved for reference
- **WHEN** a developer needs to understand or reactivate the gateway
- **THEN** the source code SHALL be available at `backend/api-gateway/` and the Helm chart SHALL be available at `deploy/charts/archive/api-gateway/`
