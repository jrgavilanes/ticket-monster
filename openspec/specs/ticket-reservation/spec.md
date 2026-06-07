## ADDED Requirements

### Requirement: Create reservation
The system SHALL allow an authenticated user with a valid queue access token to create a temporary reservation of N tickets via `POST /api/v1/reservations`. The reservation locks tickets for a configurable TTL (default 10 minutes).

#### Scenario: Successful reservation
- **WHEN** an authenticated user with a valid queue token sends `POST /api/v1/reservations` with eventId, zoneId, and quantity
- **THEN** the system SHALL atomically verify stock in PostgreSQL (`SELECT FOR UPDATE`), create a distributed lock in Redis (`SET reservation:{eventId}:{zoneId}:{userId} {userId} EX 600 NX`), decrement available stock in PostgreSQL, publish a `reservation-created` event to Redpanda, and return the reservation ID with expiration timestamp

#### Scenario: Insufficient stock
- **WHEN** a user requests more tickets than are available in the specified zone
- **THEN** the system SHALL reject the reservation with a 409 Conflict response and the current available count

#### Scenario: User exceeds ticket limit
- **WHEN** a user attempts to reserve more tickets than the configurable per-customer limit (default: 3)
- **THEN** the system SHALL reject the reservation with a 422 Unprocessable Entity response indicating the maximum allowed per customer

#### Scenario: Invalid or expired queue token
- **WHEN** a user sends a reservation request without a valid queue access token
- **THEN** the system SHALL reject the request with a 401 Unauthorized response

#### Scenario: Concurrent reservation attempt for same zone
- **WHEN** two users simultaneously attempt to reserve tickets from the same zone and stock is limited
- **THEN** the system SHALL use Redis `SET NX` to ensure only one lock is granted per ticket slot, and the second user SHALL receive a 409 Conflict if stock is exhausted

### Requirement: Query reservation
The system SHALL allow a user to query the status of their reservation via `GET /api/v1/reservations/{id}`.

#### Scenario: Query active reservation
- **WHEN** the reservation owner queries an active (non-expired, non-cancelled) reservation
- **THEN** the system SHALL return the reservation details including ticket count, zone, event, expiration time, and status `ACTIVE`

#### Scenario: Query expired reservation
- **WHEN** a user queries a reservation whose TTL has expired
- **THEN** the system SHALL return the reservation with status `EXPIRED`

#### Scenario: Query another user's reservation
- **WHEN** a user queries a reservation that belongs to a different user
- **THEN** the system SHALL return a 403 Forbidden response

### Requirement: Cancel reservation
The system SHALL allow a user to cancel their active reservation via `DELETE /api/v1/reservations/{id}`, releasing the locked tickets back to available stock.

#### Scenario: Successful cancellation
- **WHEN** the reservation owner sends `DELETE /api/v1/reservations/{id}` for an active reservation
- **THEN** the system SHALL delete the Redis lock, increment available stock in PostgreSQL, publish a `reservation-cancelled` event to Redpanda, and return status `CANCELLED`

#### Scenario: Cancel already expired reservation
- **WHEN** a user attempts to cancel an already-expired reservation
- **THEN** the system SHALL return a 409 Conflict indicating the reservation is no longer active

### Requirement: Reservation expiration via TTL
The system SHALL automatically expire reservations when the Redis TTL elapses. Expiration SHALL release the locked stock.

#### Scenario: TTL expires without payment
- **WHEN** a reservation's Redis key expires (TTL elapses without payment confirmation)
- **THEN** the system SHALL detect the expiration via Redis keyspace notification, publish a `reservation-expired` event to Redpanda, and increment available stock in PostgreSQL

#### Scenario: Fallback sweep for missed expirations
- **WHEN** a Redis keyspace notification is missed (Redis does not guarantee delivery)
- **THEN** a periodic sweep job SHALL detect expired reservations in PostgreSQL (where `expires_at < NOW()` and status is `ACTIVE`) and process their expiration

### Requirement: Anti-overbooking guarantee
The system SHALL guarantee that at no point are more tickets reserved or sold than the zone's total capacity.

#### Scenario: Atomic stock verification
- **WHEN** a reservation is created
- **THEN** the system SHALL use PostgreSQL `SELECT FOR UPDATE` on the zone's stock row to serialize concurrent access and prevent overselling

#### Scenario: Lock is exclusive
- **WHEN** a Redis lock is created for a reservation
- **THEN** the lock SHALL use `SET NX` (set if not exists) to ensure no two reservations can hold a lock on the same ticket slot simultaneously

#### Scenario: Stock is never negative
- **WHEN** stock is decremented during reservation
- **THEN** the PostgreSQL update SHALL include a `WHERE available >= requested` clause to prevent negative stock even under race conditions

### Requirement: Reservation tables use reservation schema
The system SHALL store reservation-related data in the `reservation` PostgreSQL schema. Tables `zone_stock`, `reservations`, and `reservation_items` are no longer in the `public` schema.

#### Scenario: Create reservation persists to reservation schema
- **WHEN** a reservation is created via the REST API
- **THEN** the reservation is inserted into `reservation.reservations` with items in `reservation.reservation_items`

#### Scenario: Query zone stock from reservation schema
- **WHEN** availability is queried via GraphQL
- **THEN** `zone_stock` is read from the `reservation` schema

#### Scenario: Reservation entity declares schema
- **WHEN** Hibernate maps the `Reservation` entity
- **THEN** the `@Table` annotation includes `schema = "reservation"`
