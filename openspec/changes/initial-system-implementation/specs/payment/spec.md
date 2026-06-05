## ADDED Requirements

### Requirement: Initiate payment
The system SHALL create a payment record and initiate the checkout process for an active reservation.

#### Scenario: Successful payment initiation
- **WHEN** an authenticated user sends `POST /api/v1/payments` with a valid active reservation ID
- **THEN** the system SHALL create a payment record in PostgreSQL with status PENDING, initiate the external payment provider flow (stubbed), and return the payment ID and checkout URL

#### Scenario: Payment for expired reservation
- **WHEN** a user sends `POST /api/v1/payments` with a reservation ID whose TTL has expired
- **THEN** the system SHALL reject the request with HTTP 410 Gone

#### Scenario: Payment for non-owned reservation
- **WHEN** a user sends `POST /api/v1/payments` with a reservation ID belonging to another user
- **THEN** the system SHALL reject the request with HTTP 403 Forbidden

### Requirement: Confirm payment via webhook
The system SHALL confirm a payment when receiving a webhook callback from the payment provider, converting the reservation into a confirmed sale.

#### Scenario: Successful payment confirmation
- **WHEN** the payment provider sends `POST /api/v1/payments/{id}/confirm` with a valid confirmation payload
- **THEN** the system SHALL update the payment status to CONFIRMED in PostgreSQL, publish a `payment-confirmed` event to Redpanda (consumed by Reservation Module), and return HTTP 200

#### Scenario: Duplicate confirmation (idempotency)
- **WHEN** the payment provider sends a confirmation webhook for an already-confirmed payment
- **THEN** the system SHALL detect the duplicate via idempotency key, return HTTP 200 without reprocessing, and NOT publish a duplicate event

#### Scenario: Invalid payment confirmation
- **WHEN** a confirmation webhook is received for a non-existent payment ID
- **THEN** the system SHALL return HTTP 404 Not Found

### Requirement: Query payment status
The system SHALL return the current status of a payment.

#### Scenario: Query own payment
- **WHEN** an authenticated user sends `GET /api/v1/payments/{id}` for their own payment
- **THEN** the system SHALL return the payment details including status (PENDING, CONFIRMED, FAILED), reservation ID, amount, and timestamps

#### Scenario: Query non-existent payment
- **WHEN** an authenticated user sends `GET /api/v1/payments/{id}` for a non-existent payment
- **THEN** the system SHALL return HTTP 404 Not Found

### Requirement: Payment failure handling
The system SHALL handle payment failures gracefully, releasing the associated reservation.

#### Scenario: Payment provider failure
- **WHEN** the payment provider reports a payment failure via webhook
- **THEN** the system SHALL update the payment status to FAILED in PostgreSQL, publish a `payment-failed` event to Redpanda, and the Reservation Module SHALL cancel the associated reservation and release the locked tickets

#### Scenario: Payment timeout
- **WHEN** no payment confirmation is received before the reservation's 10-minute TTL expires
- **THEN** the reservation expiry mechanism SHALL automatically release the tickets (handled by Reservation Module's TTL expiry)
