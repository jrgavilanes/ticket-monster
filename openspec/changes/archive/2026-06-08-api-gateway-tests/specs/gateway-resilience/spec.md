## ADDED Requirements

### Requirement: Circuit breaker opens on backend failure
The API Gateway SHALL open the circuit breaker when a backend module exceeds the failure threshold, and SHALL return a 503 fallback response without forwarding to the failing backend.

#### Scenario: Backend returns 500 series errors
- **WHEN** a backend module returns 500 errors exceeding the 50% failure threshold over 10 requests
- **THEN** the circuit breaker SHALL open and return a 503 Service Unavailable response from the fallback controller

#### Scenario: Backend is unreachable (connection timeout)
- **WHEN** a backend module is unreachable or times out
- **THEN** the circuit breaker SHALL open after the timeout duration and return a 503 response from the fallback controller

#### Scenario: Circuit breaker recovers when backend is healthy
- **WHEN** the circuit breaker is open and the wait duration (10s) has elapsed and a probe request succeeds
- **THEN** the circuit breaker SHALL close and resume forwarding requests normally

### Requirement: Fallback responses include module-specific error messages
Each module SHALL have a dedicated fallback endpoint that returns a 503 JSON body identifying which service is unavailable.

#### Scenario: Catalog fallback response
- **WHEN** the catalog circuit breaker is open
- **THEN** the fallback SHALL return `{"error": "Catalog service is temporarily unavailable", "status": 503}`

#### Scenario: Queue fallback response
- **WHEN** the queue circuit breaker is open
- **THEN** the fallback SHALL return `{"error": "Queue service is temporarily unavailable", "status": 503}`

#### Scenario: Reservation fallback response
- **WHEN** the reservation circuit breaker is open
- **THEN** the fallback SHALL return `{"error": "Reservation service is temporarily unavailable", "status": 503}`

#### Scenario: Payment fallback response
- **WHEN** the payment circuit breaker is open
- **THEN** the fallback SHALL return `{"error": "Payment service is temporarily unavailable", "status": 503}`
