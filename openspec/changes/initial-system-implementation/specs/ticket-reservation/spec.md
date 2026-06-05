## ADDED Requirements

### Requirement: Create ticket reservation
The system SHALL create a temporary reservation with a 10-minute TTL when a user with a valid queue access token requests tickets, using pessimistic locking to prevent overbooking.

#### Scenario: Successful reservation
- **WHEN** a user with a valid queue access token sends `POST /api/v1/reservations` with event ID, zone ID, and ticket quantity
- **THEN** the system SHALL verify stock availability in PostgreSQL using `SELECT FOR UPDATE`, create an exclusive lock in Redis with `SET NX EX 600`, persist the reservation record, publish a `reservation-created` event to Redpanda, and return the reservation with a 10-minute expiry timestamp

#### Scenario: Insufficient stock
- **WHEN** a user requests more tickets than available in the specified zone
- **THEN** the system SHALL reject the request with HTTP 409 Conflict and indicate the available quantity

#### Scenario: Ticket limit exceeded
- **WHEN** a user requests tickets exceeding the configurable per-customer limit (e.g., maximum 3 tickets per event)
- **THEN** the system SHALL reject the request with HTTP 400 Bad Request and indicate the maximum allowed

#### Scenario: Invalid or expired queue token
- **WHEN** a user sends a reservation request without a valid queue access token
- **THEN** the system SHALL reject the request with HTTP 401 Unauthorized

#### Scenario: Concurrent reservation for same seat
- **WHEN** two users attempt to reserve the same seat/zone simultaneously
- **THEN** the system SHALL grant the reservation to exactly one user (the one whose Redis `SET NX` succeeds first) and reject the other with HTTP 409 Conflict

### Requirement: View reservation
The system SHALL return reservation details including reserved tickets, expiry time, and status.

#### Scenario: Query own reservation
- **WHEN** an authenticated user sends `GET /api/v1/reservations/{id}` for their own reservation
- **THEN** the system SHALL return the reservation details including event, zone, ticket count, status (active/expired/confirmed), and remaining TTL

#### Scenario: Query another user's reservation
- **WHEN** an authenticated user sends `GET /api/v1/reservations/{id}` for a reservation belonging to a different user
- **THEN** the system SHALL reject the request with HTTP 403 Forbidden

### Requirement: Cancel reservation
The system SHALL allow users to cancel an active reservation, releasing the locked tickets.

#### Scenario: Successful cancellation
- **WHEN** an authenticated user sends `DELETE /api/v1/reservations/{id}` for their active reservation
- **THEN** the system SHALL delete the Redis lock, update the reservation status to cancelled in PostgreSQL, increment available stock, and publish a `reservation-cancelled` event to Redpanda

#### Scenario: Cancel expired reservation
- **WHEN** a user attempts to cancel a reservation whose TTL has already expired
- **THEN** the system SHALL return HTTP 410 Gone indicating the reservation no longer exists

### Requirement: Reservation expiry
The system SHALL automatically release tickets when a reservation's 10-minute TTL expires without payment confirmation.

#### Scenario: TTL expiration
- **WHEN** a Redis reservation key expires (TTL reached)
- **THEN** a Redis keyspace notification listener SHALL publish a `reservation-expired` event to Redpanda, and the Reservation Module SHALL update the reservation status to expired and increment available stock in PostgreSQL

#### Scenario: Payment confirmed before expiry
- **WHEN** a `payment-confirmed` event is received from Redpanda before the reservation TTL expires
- **THEN** the system SHALL convert the reservation to a confirmed sale, remove the Redis TTL (make the lock permanent), and update the reservation status to confirmed

### Requirement: Zero overbooking guarantee
The system SHALL NEVER allow more tickets to be reserved or sold than the venue's capacity for any zone.

#### Scenario: Stock reaches zero
- **WHEN** the last available ticket for a zone is reserved
- **THEN** any subsequent reservation request for that zone SHALL be rejected with HTTP 409 Conflict and available count of 0

#### Scenario: Released tickets become available again
- **WHEN** a reservation expires or is cancelled
- **THEN** the released tickets SHALL immediately become available for new reservations
