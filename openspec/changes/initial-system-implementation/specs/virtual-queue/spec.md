## ADDED Requirements

### Requirement: Join virtual queue
The system SHALL enqueue authenticated users into a FIFO queue for a specific event when they request to purchase tickets.

#### Scenario: Successful queue join
- **WHEN** an authenticated user sends `POST /api/v1/queue/{eventId}/join`
- **THEN** the system SHALL enqueue the user in the Redis FIFO list and return a queue ticket ID and estimated position

#### Scenario: Duplicate queue join
- **WHEN** a user who is already in the queue for an event sends another join request
- **THEN** the system SHALL reject the request with HTTP 409 Conflict and return the existing queue position

#### Scenario: Queue for non-existent event
- **WHEN** a user sends a join request for an invalid event ID
- **THEN** the system SHALL reject the request with HTTP 404 Not Found

### Requirement: Check queue position
The system SHALL allow users to query their current position in the virtual queue.

#### Scenario: User is in queue
- **WHEN** an authenticated user sends `GET /api/v1/queue/{eventId}/status` and is in the queue
- **THEN** the system SHALL return the user's current position, total queue size, and estimated wait time

#### Scenario: User not in queue
- **WHEN** an authenticated user sends `GET /api/v1/queue/{eventId}/status` and is NOT in the queue
- **THEN** the system SHALL return HTTP 404 indicating the user is not in the queue

#### Scenario: User turn has arrived
- **WHEN** the user's position reaches the front of the queue (position 0)
- **THEN** the system SHALL indicate that the user's turn has arrived and they can obtain an access token

### Requirement: Obtain queue access token
The system SHALL issue a short-lived JWT access token when the user reaches the front of the queue, granting permission to make reservations.

#### Scenario: Token issuance at front of queue
- **WHEN** a user at position 0 sends `GET /api/v1/queue/{eventId}/token`
- **THEN** the system SHALL issue a JWT with the user ID, event ID, and a configurable TTL (e.g., 5 minutes) and remove the user from the queue

#### Scenario: Token request before turn
- **WHEN** a user NOT at position 0 sends `GET /api/v1/queue/{eventId}/token`
- **THEN** the system SHALL reject the request with HTTP 403 Forbidden and indicate the user's current position

#### Scenario: Token expiration without use
- **WHEN** an issued access token expires without being used for a reservation
- **THEN** the system SHALL release the slot and advance the next user in the queue

### Requirement: Batch dispatching
The system SHALL release users from the queue in configurable batches to control downstream load on the reservation module.

#### Scenario: Scheduled batch release
- **WHEN** the batch dispatcher timer fires (configurable interval, e.g., every 2 seconds)
- **THEN** the system SHALL dequeue up to N users (configurable, e.g., 500) and mark them as ready for token issuance

#### Scenario: Queue smaller than batch size
- **WHEN** the dispatcher fires and fewer than N users are in the queue
- **THEN** the system SHALL dequeue all remaining users

#### Scenario: Backpressure protection
- **WHEN** the reservation module is under heavy load (circuit breaker open or rate limit reached)
- **THEN** the dispatcher SHALL pause batch releases until the downstream module recovers
