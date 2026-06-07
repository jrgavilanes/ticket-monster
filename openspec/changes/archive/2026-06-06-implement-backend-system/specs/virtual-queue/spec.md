## ADDED Requirements

### Requirement: Join virtual queue
The system SHALL allow an authenticated user to join the virtual queue for a specific event via `POST /api/v1/queue/{eventId}/join`. The user is assigned a position in a Redis FIFO queue.

#### Scenario: User joins queue successfully
- **WHEN** an authenticated user sends `POST /api/v1/queue/{eventId}/join` and is not already in the queue
- **THEN** the system SHALL enqueue the user in Redis, return a queue ticket ID and estimated position

#### Scenario: User already in queue
- **WHEN** an authenticated user sends `POST /api/v1/queue/{eventId}/join` and is already enqueued for that event
- **THEN** the system SHALL return the existing queue ticket ID and current position without creating a duplicate entry

#### Scenario: Queue not active for event
- **WHEN** a user attempts to join the queue for an event that does not have an active virtual queue
- **THEN** the system SHALL return a 404 response indicating no active queue for the event

### Requirement: Check queue position
The system SHALL allow a user to check their current position in the queue via `GET /api/v1/queue/{eventId}/status`.

#### Scenario: User checks position while waiting
- **WHEN** a user with a valid queue ticket sends `GET /api/v1/queue/{eventId}/status`
- **THEN** the system SHALL return the user's current position in the queue and estimated wait time

#### Scenario: User's turn has arrived
- **WHEN** a user whose batch has been dispatched sends `GET /api/v1/queue/{eventId}/status`
- **THEN** the system SHALL return a status of `TURN_READY` with instructions to obtain an access token

### Requirement: Obtain access token
The system SHALL issue a short-lived JWT access token to users whose turn has arrived, via `GET /api/v1/queue/{eventId}/token`. This token is required to access the Reservation Module.

#### Scenario: User obtains token when turn arrives
- **WHEN** a user whose batch has been dispatched requests `GET /api/v1/queue/{eventId}/token`
- **THEN** the system SHALL issue a JWT with a configurable TTL (default 5 minutes) containing the user ID, event ID, and queue ticket claim

#### Scenario: User requests token before turn
- **WHEN** a user whose batch has NOT been dispatched requests `GET /api/v1/queue/{eventId}/token`
- **THEN** the system SHALL return a 403 response indicating the user's turn has not yet arrived

#### Scenario: Token expires without use
- **WHEN** a queue access token expires without the user making a reservation
- **THEN** the system SHALL release the user's slot and advance the next batch in the queue

### Requirement: Batch dispatcher
The system SHALL run a scheduled dispatcher that releases batches of N users from the queue every X seconds (configurable, default: 500 users every 2 seconds).

#### Scenario: Dispatcher releases a batch
- **WHEN** the dispatcher tick fires and there are users waiting in the queue
- **THEN** the system SHALL dequeue up to N users, mark their status as `TURN_READY`, and notify them (via status polling)

#### Scenario: Queue has fewer users than batch size
- **WHEN** the dispatcher tick fires and fewer than N users are in the queue
- **THEN** the system SHALL release all remaining users in the queue

#### Scenario: Dispatcher handles empty queue
- **WHEN** the dispatcher tick fires and the queue is empty
- **THEN** the system SHALL perform no action and wait for the next tick

### Requirement: Queue stored in Redis only
The virtual queue SHALL be stored entirely in Redis using FIFO list operations (LPUSH/BRPOP). No queue data SHALL be persisted to disk-based databases.

#### Scenario: Redis contains active queue
- **WHEN** users are actively waiting in the queue
- **THEN** the queue data SHALL exist only in Redis memory with no disk-based backup

#### Scenario: Redis restarts during active queue
- **WHEN** Redis restarts and loses in-memory queue data
- **THEN** the system SHALL accept new queue joins and rebuild the queue from new arrivals (previous queue positions are lost — acceptable trade-off documented in design)
