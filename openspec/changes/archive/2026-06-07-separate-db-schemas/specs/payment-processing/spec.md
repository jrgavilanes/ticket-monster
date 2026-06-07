## MODIFIED Requirements

### Requirement: Payment tables use payment schema
The system SHALL store payment-related data in the `payment` PostgreSQL schema. Tables `payments` and `payment_audit` are no longer in the `public` schema.

#### Scenario: Initiate payment persists to payment schema
- **WHEN** a payment is initiated via the REST API
- **THEN** the payment is inserted into `payment.payments` with audit entry in `payment.payment_audit`

#### Scenario: Confirm payment queries payment schema
- **WHEN** a payment is confirmed via the REST API
- **THEN** the payment is looked up in `payment.payments` by ID

#### Scenario: Payment entity declares schema
- **WHEN** Hibernate maps the `Payment` entity
- **THEN** the `@Table` annotation includes `schema = "payment"`
