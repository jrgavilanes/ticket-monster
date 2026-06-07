## ADDED Requirements

### Requirement: Initiate payment
The system SHALL allow a user with an active reservation to initiate a payment via `POST /api/v1/payments`. The payment record is created in PostgreSQL with status `PENDING`.

#### Scenario: Successful payment initiation
- **WHEN** an authenticated user with an active reservation sends `POST /api/v1/payments` with reservationId and payment method details
- **THEN** the system SHALL create a payment record in PostgreSQL with status `PENDING`, generate an idempotency key, and return the payment ID and redirect URL (or mock confirmation)

#### Scenario: Payment for expired reservation
- **WHEN** a user attempts to initiate payment for a reservation that has already expired
- **THEN** the system SHALL reject the request with a 409 Conflict indicating the reservation is no longer active

#### Scenario: Duplicate payment attempt
- **WHEN** a user sends a payment request with an idempotency key that has already been processed
- **THEN** the system SHALL return the existing payment record without creating a duplicate

### Requirement: Query payment status
The system SHALL allow a user to check the status of their payment via `GET /api/v1/payments/{id}`.

#### Scenario: Query pending payment
- **WHEN** a user queries a payment with status `PENDING`
- **THEN** the system SHALL return the payment details including amount, status, and creation timestamp

#### Scenario: Query confirmed payment
- **WHEN** a user queries a payment with status `CONFIRMED`
- **THEN** the system SHALL return the payment details including confirmation timestamp and transaction reference

#### Scenario: Query another user's payment
- **WHEN** a user queries a payment that belongs to a different user
- **THEN** the system SHALL return a 403 Forbidden response

### Requirement: Confirm payment (webhook)
The system SHALL accept payment confirmation from the payment gateway via `POST /api/v1/payments/{id}/confirm`. This endpoint is idempotent.

#### Scenario: Successful payment confirmation
- **WHEN** the payment gateway sends a confirmation webhook for a `PENDING` payment
- **THEN** the system SHALL update the payment status to `CONFIRMED` in PostgreSQL, publish a `payment-confirmed` event to Redpanda (which triggers the Reservation Module to convert the temporary lock into a permanent sale), and return 200 OK

#### Scenario: Duplicate confirmation (idempotency)
- **WHEN** the payment gateway sends a confirmation webhook for an already `CONFIRMED` payment
- **THEN** the system SHALL detect the duplicate via the idempotency key, return 200 OK without reprocessing, and NOT publish a duplicate event

#### Scenario: Confirmation for non-existent payment
- **WHEN** a confirmation webhook arrives for a payment ID that does not exist
- **THEN** the system SHALL return a 404 Not Found response

### Requirement: Payment converts reservation to sale
The system SHALL convert a temporary reservation into a permanent sale when the `payment-confirmed` event is received.

#### Scenario: Reservation becomes permanent sale
- **WHEN** the Reservation Module consumes a `payment-confirmed` event from Redpanda
- **THEN** the system SHALL update the reservation status to `SOLD` in PostgreSQL, remove the Redis TTL (make the lock permanent or delete it since the sale is now in PostgreSQL), and persist the sale record

#### Scenario: Payment confirmed for expired reservation
- **WHEN** a `payment-confirmed` event arrives for a reservation that has already expired
- **THEN** the system SHALL log the inconsistency, publish a `payment-refund-required` event, and NOT create a sale

### Requirement: Payment data stored in PostgreSQL
All payment records and transactions SHALL be stored in PostgreSQL with ACID guarantees and audit trail.

#### Scenario: Payment record persistence
- **WHEN** a payment is created, confirmed, or failed
- **THEN** the system SHALL persist the full payment record in PostgreSQL with timestamps, amounts, status transitions, and idempotency keys

#### Scenario: Audit trail
- **WHEN** a payment undergoes status transitions
- **THEN** the system SHALL record each transition as an audit entry in PostgreSQL with the previous status, new status, timestamp, and triggering actor (user or system)
