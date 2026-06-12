## ADDED Requirements

### Requirement: Traefik ingress routes all requests to monolith
All HTTP requests to the domain SHALL be routed by Traefik directly to the monolith service without intermediate routing between backend modules.

#### Scenario: GraphQL request to monolith
- **WHEN** a request is sent to `https://janrax.es/graphql`
- **THEN** Traefik SHALL forward the request directly to the ticketmonster service

#### Scenario: Queue request to monolith
- **WHEN** a request is sent to `https://janrax.es/api/v1/queue/{eventId}/join`
- **THEN** Traefik SHALL forward the request directly to the ticketmonster service

## REMOVED Requirements

### Requirement: Gateway routes requests by path
**Reason**: With a single monolith, the API Gateway is not needed to route between backend modules. All requests go directly to the monolith service.
**Migration**: Traefik ingress forwards all traffic to the ticketmonster service. No per-module routing is required.
