## ADDED Requirements

### Requirement: Rate limiter key resolves from X-User-Id header
The API Gateway SHALL resolve the rate limiter key from the `X-User-Id` request header when present.

#### Scenario: X-User-Id header present
- **WHEN** a request includes the `X-User-Id` header with value `user-123`
- **THEN** the rate limiter key resolver SHALL return `user-123`

#### Scenario: X-User-Id header absent
- **WHEN** a request does not include the `X-User-Id` header
- **THEN** the rate limiter key resolver SHALL fall back to the remote IP address of the client

#### Scenario: No X-User-Id and no remote address
- **WHEN** a request does not include the `X-User-Id` header and the remote address is not available
- **THEN** the rate limiter key resolver SHALL return `anonymous`
