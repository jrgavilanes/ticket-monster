## ADDED Requirements

### Requirement: Gateway routes requests by path
The API Gateway SHALL route requests to the appropriate backend module based on the request path.

#### Scenario: Catalog GraphQL route
- **WHEN** a request is sent to `/graphql`
- **THEN** the gateway SHALL forward the request to the Catalog module

#### Scenario: Queue module route
- **WHEN** a request is sent to `/api/v1/queue/{eventId}/join`
- **THEN** the gateway SHALL forward the request to the Virtual Queue module

#### Scenario: Reservation module route
- **WHEN** a request is sent to `/api/v1/reservations`
- **THEN** the gateway SHALL forward the request to the Reservation module

#### Scenario: Payment module route
- **WHEN** a request is sent to `/api/v1/payments`
- **THEN** the gateway SHALL forward the request to the Payment module

#### Scenario: Unknown path returns 404
- **WHEN** a request is sent to an undefined path
- **THEN** the gateway SHALL return a 404 Not Found response
