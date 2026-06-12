## ADDED Requirements

### Requirement: Circuit breaking via Resilience4j in-monolith
The monolith SHALL use Resilience4j circuit breakers to protect downstream dependencies (PostgreSQL, MongoDB, Redis, Redpanda) from cascade failures. The monolith already includes Resilience4j as a dependency and exposes circuit breaker metrics via Actuator for Prometheus.

#### Scenario: Database dependency fails
- **WHEN** PostgreSQL returns errors exceeding the circuit breaker threshold
- **THEN** Resilience4j SHALL open the circuit breaker and the monolith SHALL return an appropriate error response without crashing

#### Scenario: Circuit breaker recovers
- **WHEN** the circuit breaker is open and the wait duration elapses and a probe request succeeds
- **THEN** the circuit breaker SHALL half-open, allow a probe request, and close if the probe succeeds

## REMOVED Requirements

### Requirement: Circuit breaker opens on backend failure
**Reason**: The API Gateway's circuit breakers for each module route are no longer needed — there is only one backend service (the monolith). In-monolith circuit breaking via Resilience4j replaces this at the dependency level.
**Migration**: Resilience4j circuit breakers in the monolith protect individual dependencies. No gateway-level module-based circuit breakers are needed.

### Requirement: Fallback responses include module-specific error messages
**Reason**: With the gateway removed, the `FallbackController` and its module-specific error responses are no longer deployed.
**Migration**: The monolith's own exception handling (`@ControllerAdvice`) provides error responses with traceId for observability correlation.
