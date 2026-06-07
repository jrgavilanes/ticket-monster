## ADDED Requirements

### Requirement: Docker Compose for local development
The system SHALL include a `docker-compose.yml` file at the project root that provisions all infrastructure dependencies and application services for local development and testing.

#### Scenario: Start full local environment
- **WHEN** a developer runs `docker compose up` from the project root
- **THEN** the system SHALL start PostgreSQL, MongoDB, Redis, Redpanda (with console UI), Keycloak, and the Grafana stack (Prometheus, Loki, Tempo, Grafana) with all required configuration and networking

#### Scenario: Application services included
- **WHEN** the full stack is started
- **THEN** the `ticketmonster` monolith and `api-gateway` SHALL also start, connected to all infrastructure services, with hot-reload enabled for development (Spring Boot DevTools or volume-mounted source)

#### Scenario: Infrastructure services only
- **WHEN** a developer runs `docker compose up postgres mongodb redis redpanda keycloak`
- **THEN** only the specified infrastructure services SHALL start, allowing the developer to run the application locally on the host machine (e.g., via IDE)

### Requirement: Persistent volumes for data
The Docker Compose setup SHALL use named volumes for databases and Redis to preserve data across container restarts.

#### Scenario: Data persists after restart
- **WHEN** a developer stops and restarts the Docker Compose environment
- **THEN** PostgreSQL, MongoDB, and Redis data SHALL be preserved via named volumes

#### Scenario: Clean slate
- **WHEN** a developer runs `docker compose down -v`
- **THEN** all named volumes SHALL be removed, providing a clean slate for the next `docker compose up`

### Requirement: Pre-configured Keycloak realm
The Docker Compose Keycloak service SHALL auto-import a pre-configured realm with Ticket Monster clients, roles, and test users.

#### Scenario: Keycloak starts with realm
- **WHEN** Keycloak container starts
- **THEN** it SHALL auto-import a realm named `ticket-monster` with two clients (`ticket-monster-app`, `api-gateway`), roles (`USER`, `ADMIN`), and two test users (`admin/admin` with ADMIN role, `user/user` with USER role)

### Requirement: Pre-configured Grafana dashboards
The Docker Compose Grafana service SHALL auto-provision data sources and dashboards.

#### Scenario: Grafana starts with data sources
- **WHEN** Grafana container starts
- **THEN** it SHALL auto-provision Prometheus, Loki, and Tempo as data sources and import the system overview and module-specific dashboards

### Requirement: Health checks for all services
The Docker Compose file SHALL include health checks for all services to ensure proper startup ordering.

#### Scenario: Application waits for infrastructure
- **WHEN** the ticketmonster and api-gateway services start
- **THEN** they SHALL wait (via `depends_on` with `condition: service_healthy`) for PostgreSQL, MongoDB, Redis, Redpanda, and Keycloak to be healthy before starting

### Requirement: Environment configuration via .env file
The Docker Compose setup SHALL use a `.env.example` file documenting all configurable environment variables.

#### Scenario: Default configuration works
- **WHEN** a developer copies `.env.example` to `.env` without modifications and runs `docker compose up`
- **THEN** the full stack SHALL start with sensible defaults for local development

#### Scenario: Custom configuration
- **WHEN** a developer modifies values in `.env` (e.g., database passwords, ports)
- **THEN** Docker Compose SHALL use the custom values for all services

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
