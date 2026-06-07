## MODIFIED Requirements

### Requirement: PostgreSQL schema initialization at container start
The local development environment SHALL initialize PostgreSQL schemas via a mounted init script at `docker/postgres/init/01-create-schemas.sql`.

#### Scenario: Schemas created on first docker compose up
- **WHEN** `docker compose --profile dev up` is run for the first time
- **THEN** schemas `reservation`, `payment`, and `keycloak` are created automatically

### Requirement: Keycloak uses dedicated schema
The Keycloak service in docker-compose SHALL use environment variable `KC_DB_SCHEMA=keycloak` to isolate its tables.

#### Scenario: Keycloak starts and creates tables in keycloak schema
- **WHEN** Keycloak container starts
- **THEN** its tables are created in the `keycloak` schema instead of `public`

### Requirement: Flyway auto-config disabled
Spring Boot Flyway auto-configuration SHALL be disabled (`spring.flyway.enabled: false`). Manual Flyway beans handle migrations.

#### Scenario: Auto-configured Flyway does not run
- **WHEN** the Spring context loads
- **THEN** no Flyway migration runs from the default auto-configuration

### Requirement: JPA ddl-auto set to none
Hibernate SHALL not manage DDL operations (`spring.jpa.hibernate.ddl-auto: none`). Flyway handles all schema creation.

#### Scenario: Hibernate does not create or validate tables
- **WHEN** the Spring context loads with a clean database
- **THEN** Hibernate does not attempt to create, update, or validate the schema
