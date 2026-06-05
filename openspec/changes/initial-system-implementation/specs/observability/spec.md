## ADDED Requirements

### Requirement: Structured logging
The system SHALL produce structured JSON logs via Logback, shipped to Loki for centralized log aggregation and querying.

#### Scenario: Application log output
- **WHEN** any module processes a request or event
- **THEN** the system SHALL emit structured JSON logs containing timestamp, level, module name, trace ID, span ID, user ID (if authenticated), and message to stdout

#### Scenario: Log querying in Grafana
- **WHEN** an operator queries logs in Grafana's Loki datasource
- **THEN** the system SHALL return logs filterable by module, level, trace ID, user ID, and time range

### Requirement: Application metrics
The system SHALL expose Prometheus-compatible metrics via Spring Boot Actuator and Micrometer for monitoring system health and performance.

#### Scenario: Metrics endpoint availability
- **WHEN** Prometheus scrapes the `/actuator/prometheus` endpoint
- **THEN** the system SHALL return metrics including JVM stats, HTTP request count/latency, Redis operation count/latency, PostgreSQL query metrics, Redpanda consumer/producer metrics, and custom business metrics (reservations created, payments confirmed, queue depth)

#### Scenario: Custom business metrics
- **WHEN** a reservation is created, expired, or confirmed
- **THEN** the system SHALL increment the corresponding counter metric (e.g., `reservations_created_total`, `reservations_expired_total`, `reservations_confirmed_total`)

#### Scenario: Metrics visualization in Grafana
- **WHEN** an operator views the application dashboard in Grafana
- **THEN** the system SHALL display panels for request rate, error rate, latency percentiles (p50, p95, p99), queue depth, reservation throughput, and payment success rate

### Requirement: Distributed tracing
The system SHALL produce OpenTelemetry traces via the Java Agent, exported to Tempo for distributed trace visualization.

#### Scenario: Trace generation on HTTP request
- **WHEN** an HTTP request enters the API Gateway
- **THEN** the system SHALL create a trace span at the gateway and propagate the trace context (W3C Trace Context headers) to downstream modules, creating child spans for each module, database query, Redis operation, and Redpanda publish/consume

#### Scenario: Trace visualization in Grafana
- **WHEN** an operator searches for a trace in Grafana's Tempo datasource by trace ID
- **THEN** the system SHALL display the full request flow as a waterfall diagram showing all spans, durations, and tags

### Requirement: Grafana dashboards
The system SHALL provide pre-configured Grafana dashboards for system overview, module-level metrics, and infrastructure health.

#### Scenario: System overview dashboard
- **WHEN** an operator opens the "Ticket Monster Overview" dashboard
- **THEN** the system SHALL display panels for total requests/sec, error rate, active reservations, queue depth, payment throughput, and module health status

#### Scenario: Infrastructure dashboard
- **WHEN** an operator opens the "Infrastructure" dashboard
- **THEN** the system SHALL display panels for Redis memory/connections, PostgreSQL connections/query time, Redpanda consumer lag/partitions, MongoDB operations, and K3s pod CPU/memory utilization
