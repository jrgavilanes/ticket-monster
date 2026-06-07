## MODIFIED Requirements

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
