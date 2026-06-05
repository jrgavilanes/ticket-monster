## ADDED Requirements

### Requirement: K3s cluster provisioning
The system SHALL provide an idempotent script (`provision-k3s.sh`) that configures a remote Debian 12 VPS with K3s, k9s, and Helm.

#### Scenario: First-time cluster setup
- **WHEN** an operator runs `./provision-k3s.sh -u <user@host> -d <domain>` on a fresh Debian 12 VPS
- **THEN** the script SHALL install K3s, k9s, and Helm, configure kubeconfig for remote access, and output connection details

#### Scenario: Idempotent re-run
- **WHEN** an operator runs `provision-k3s.sh` on an already-provisioned VPS
- **THEN** the script SHALL detect existing installations and skip redundant steps without errors

### Requirement: Infrastructure provisioning
The system SHALL provide an idempotent script (`provision-infra.sh`) that deploys all infrastructure components to K3s via Helm.

#### Scenario: Full infrastructure deployment
- **WHEN** an operator runs `./provision-infra.sh -u <user@host> -d <domain>`
- **THEN** the script SHALL deploy cert-manager (with Let's Encrypt ClusterIssuer), Redpanda, Redis, PostgreSQL, MongoDB, and the Grafana observability stack (Prometheus, Loki, Tempo, Grafana) using Helm charts

#### Scenario: Secret generation
- **WHEN** the infrastructure provisioning script runs
- **THEN** the script SHALL generate Kubernetes secrets with `openssl rand -hex 32` for database passwords and API keys, skipping generation if secrets already exist (idempotent)

#### Scenario: Idempotent re-run
- **WHEN** an operator runs `provision-infra.sh` on a cluster with existing infrastructure
- **THEN** the script SHALL detect existing deployments and secrets, performing `helm upgrade` instead of `helm install`, and skip secret regeneration

### Requirement: Application deployment
The system SHALL provide an idempotent script (`provision-services.sh`) that deploys the application and runs load tests.

#### Scenario: Application deployment
- **WHEN** an operator runs `./provision-services.sh -u <user@host> -d <domain>`
- **THEN** the script SHALL build the Docker image, run `helm upgrade --install` for the monolith and API Gateway charts, configure ingress with TLS, and execute k6 load tests

#### Scenario: Rolling update
- **WHEN** an operator runs `provision-services.sh` with an already-deployed application
- **THEN** the script SHALL perform a rolling update via Helm without downtime

### Requirement: Helm chart structure
The system SHALL package the application as Helm charts with configurable values for all environment-specific settings.

#### Scenario: Chart values configuration
- **WHEN** deploying with Helm
- **THEN** the chart SHALL accept configurable values for replica count, resource limits, database connection strings (via secretRef), Redis URL, Redpanda brokers, Keycloak URL, and ingress host/TLS

#### Scenario: Secret consumption
- **WHEN** the application pod starts
- **THEN** it SHALL consume infrastructure secrets created by `provision-infra.sh` via `envFrom: secretRef`

### Requirement: Horizontal Pod Autoscaler
The system SHALL configure HPA for the monolith pods to auto-scale based on CPU and memory utilization.

#### Scenario: Scale up under load
- **WHEN** CPU utilization of monolith pods exceeds the configured threshold (e.g., 70%)
- **THEN** K3s HPA SHALL automatically create additional pod replicas up to the configured maximum

#### Scenario: Scale down under low load
- **WHEN** CPU utilization drops below the configured threshold for a sustained period
- **THEN** K3s HPA SHALL reduce pod replicas to the configured minimum

### Requirement: Load testing
The system SHALL include k6 load test scripts that validate system behavior under simulated high-concurrency scenarios.

#### Scenario: Mass sale simulation
- **WHEN** the k6 load test script runs
- **THEN** it SHALL simulate concurrent users joining the virtual queue, obtaining tokens, creating reservations, and completing payments, and report metrics for throughput, latency, and error rates

#### Scenario: Overbooking prevention under load
- **WHEN** the k6 load test simulates more reservation requests than available tickets
- **THEN** the test SHALL verify that exactly the available number of reservations succeed and all excess requests are rejected without overbooking
