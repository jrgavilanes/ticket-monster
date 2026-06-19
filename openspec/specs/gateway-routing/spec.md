## ADDED Requirements

### Requirement: Traefik ingress routes all requests to monolith
All HTTP requests to the domain SHALL be routed by Traefik directly to the monolith service without intermediate routing between backend modules.

#### Scenario: GraphQL request to monolith
- **WHEN** a request is sent to `https://janrax.es/graphql`
- **THEN** Traefik SHALL forward the request directly to the ticketmonster service

#### Scenario: Queue request to monolith
- **WHEN** a request is sent to `https://janrax.es/api/v1/queue/{eventId}/join`
- **THEN** Traefik SHALL forward the request directly to the ticketmonster service
