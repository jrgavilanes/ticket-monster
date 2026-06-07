## ADDED Requirements

### Requirement: Module-level PostgreSQL schemas
The system SHALL organize PostgreSQL tables into separate schemas per bounded context: `reservation` for the reservation module, `payment` for the payment module, and `keycloak` for Keycloak internal tables.

#### Scenario: Reservation tables in reservation schema
- **WHEN** Flyway migration for reservation module executes
- **THEN** tables `zone_stock`, `reservations`, and `reservation_items` are created in the `reservation` schema

#### Scenario: Payment tables in payment schema
- **WHEN** Flyway migration for payment module executes
- **THEN** tables `payments` and `payment_audit` are created in the `payment` schema

#### Scenario: Keycloak tables in keycloak schema
- **WHEN** Keycloak starts with `KC_DB_SCHEMA=keycloak`
- **THEN** Keycloak internal tables are created in the `keycloak` schema

### Requirement: JPA entity schema binding
All JPA entities SHALL declare their target schema via `@Table(schema = "...")` annotation.

#### Scenario: Payment entity targets payment schema
- **WHEN** Hibernate generates SQL for the Payment entity
- **THEN** the table reference is `payment.payments`

#### Scenario: Reservation entity targets reservation schema
- **WHEN** Hibernate generates SQL for the Reservation entity
- **THEN** the table reference is `reservation.reservations`

### Requirement: Per-schema Flyway migrations
Flyway migrations SHALL be organized in schema-specific directories (`db/migration/reservation/` and `db/migration/payment/`) and executed by independent Flyway instances.

#### Scenario: Reservation Flyway runs independently
- **WHEN** the `reservationFlyway` bean initializes
- **THEN** it executes migrations from `classpath:db/migration/reservation/` against the `reservation` schema

#### Scenario: Payment Flyway runs independently
- **WHEN** the `paymentFlyway` bean initializes
- **THEN** it executes migrations from `classpath:db/migration/payment/` against the `payment` schema

#### Scenario: Separate migration history tables
- **WHEN** both Flyway instances run against the same database
- **THEN** each maintains its own `flyway_schema_history` table within its respective schema

### Requirement: Schema initialization at container start
PostgreSQL schemas SHALL be created automatically when the container initializes for the first time.

#### Scenario: First container start
- **WHEN** the PostgreSQL container starts with an empty data volume
- **THEN** the init script `01-create-schemas.sql` creates schemas `reservation`, `payment`, and `keycloak`

#### Scenario: Subsequent container starts
- **WHEN** the PostgreSQL container restarts with an existing data volume
- **THEN** the init script does not run (schemas already exist)
