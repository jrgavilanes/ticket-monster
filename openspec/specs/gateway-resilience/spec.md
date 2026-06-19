## ADDED Requirements

### Requirement: Circuit breaking via Resilience4j in-monolith
The monolith SHALL use Resilience4j circuit breakers to protect downstream dependencies (PostgreSQL, MongoDB, Redis, Redpanda) from cascade failures. The monolith already includes Resilience4j as a dependency and exposes circuit breaker metrics via Actuator for Prometheus.

#### Scenario: Database dependency fails
- **WHEN** PostgreSQL returns errors exceeding the circuit breaker threshold
- **THEN** Resilience4j SHALL open the circuit breaker and the monolith SHALL return an appropriate error response without crashing

#### Scenario: Circuit breaker recovers
- **WHEN** the circuit breaker is open and the wait duration elapses and a probe request succeeds
- **THEN** the circuit breaker SHALL half-open, allow a probe request, and close if the probe succeeds
