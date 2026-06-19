## ADDED Requirements

### Requirement: Structured logging with Loki
The system SHALL produce structured JSON logs via Logback, shipped to Loki for centralized log aggregation and querying.

#### Scenario: Application logs are structured JSON
- **WHEN** any module logs a message (info, warn, error)
- **THEN** the log output SHALL be in JSON format containing timestamp, level, logger name, message, trace ID, span ID, and module name

#### Scenario: Logs are queryable in Grafana
- **WHEN** an operator queries logs in Grafana's Loki data source
- **THEN** logs SHALL be filterable by module, level, trace ID, and time range

### Requirement: Metrics with Prometheus and Micrometer
The system SHALL expose application metrics via Spring Boot Actuator's `/actuator/prometheus` endpoint, scraped by Prometheus.

#### Scenario: HTTP request metrics
- **WHEN** the application processes HTTP requests
- **THEN** the system SHALL expose request count, request duration histogram, and error count metrics labeled by endpoint, method, and status code

#### Scenario: Custom business metrics
- **WHEN** reservations are created, expired, or confirmed
- **THEN** the system SHALL expose counters for each reservation state transition and a gauge for current active reservations

#### Scenario: JVM and system metrics
- **WHEN** Prometheus scrapes the metrics endpoint
- **THEN** the system SHALL expose JVM memory, GC, thread count, and connection pool metrics

#### Scenario: Metrics visualized in Grafana
- **WHEN** an operator views the Grafana dashboard
- **THEN** pre-configured dashboards SHALL display request rate, latency percentiles (p50, p95, p99), error rate, active reservations, and queue depth

### Requirement: Distributed tracing with Tempo and OpenTelemetry
The system SHALL produce distributed traces via OpenTelemetry Java Agent, exported to Tempo for trace storage and querying.

#### Scenario: Trace spans across modules
- **WHEN** a request flows through the API Gateway into multiple modules (e.g., Queue → Reservation → Redpanda → Payment)
- **THEN** the system SHALL produce a single trace with spans for each module interaction, linked by a shared trace ID

#### Scenario: Trace queryable in Grafana
- **WHEN** an operator searches for a trace by trace ID in Grafana's Tempo data source
- **THEN** the full span tree SHALL be displayed with timing, module names, and any error annotations

#### Scenario: Log-trace correlation
- **WHEN** a log entry contains a trace ID
- **THEN** the operator SHALL be able to navigate from the log entry to the corresponding trace in Grafana

### Requirement: Grafana dashboards
The system SHALL include pre-configured Grafana dashboards for operational monitoring.

#### Scenario: System overview dashboard
- **WHEN** an operator opens the system overview dashboard
- **THEN** the dashboard SHALL display request rate, error rate, latency percentiles, active reservations, queue depth, and pod count

#### Scenario: Module-specific dashboard
- **WHEN** an operator opens a module-specific dashboard (e.g., Reservation Module)
- **THEN** the dashboard SHALL display module-specific metrics: reservation creation rate, expiration rate, lock contention, PostgreSQL query latency, and Redis operation latency

### Requirement: Grafana access is role-gated via Keycloak OAuth
The Grafana stack SHALL only grant access to Keycloak-authenticated users who possess one of the `grafana-viewer`, `grafana-editor`, or `grafana-admin` realm roles. Users without a grafana role SHALL be denied access.

#### Scenario: Authorized user accesses Grafana dashboards
- **WHEN** an operator with `grafana-viewer` role logs into Grafana via Keycloak OAuth
- **THEN** the system overview dashboard and module-specific dashboards SHALL be visible in read-only mode

#### Scenario: Unauthorized user is denied
- **WHEN** a Keycloak-authenticated user without any `grafana-*` role attempts to log into Grafana
- **THEN** the login SHALL be rejected and no Grafana resources SHALL be accessible

#### Scenario: Admin user manages datasources
- **WHEN** an operator with `grafana-admin` role logs into Grafana
- **THEN** the operator SHALL be able to add, edit, and remove datasources and manage organization settings
