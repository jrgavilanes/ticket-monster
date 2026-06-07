## ADDED Requirements

### Requirement: K3s cluster provisioning script
The system SHALL include an idempotent script `provision-k3s.sh` that configures a remote Debian 12 VPS with K3s, k9s, and Helm.

#### Scenario: Fresh VPS provisioning
- **WHEN** the script is run against a fresh Debian 12 VPS with SSH access (`./provision-k3s.sh -u user@host -d domain`)
- **THEN** the script SHALL install K3s, k9s, and Helm, configure kubeconfig for remote access, and output connection instructions

#### Scenario: Re-run on already provisioned VPS
- **WHEN** the script is run against a VPS that already has K3s installed
- **THEN** the script SHALL detect the existing installation, skip redundant steps, and exit successfully without modifying the running cluster

### Requirement: Infrastructure provisioning script
The system SHALL include an idempotent script `provision-infra.sh` that deploys all infrastructure components to K3s via Helm.

#### Scenario: Fresh infrastructure deployment
- **WHEN** the script is run against a K3s cluster with no existing infrastructure
- **THEN** the script SHALL deploy cert-manager (with Let's Encrypt ClusterIssuer), Redpanda, Redis, PostgreSQL, MongoDB, Keycloak, and the Grafana stack (Prometheus, Loki, Tempo, Grafana) using Helm charts

#### Scenario: Secrets auto-generation
- **WHEN** the script deploys infrastructure for the first time
- **THEN** the script SHALL generate secrets using `openssl rand -hex 32` and store them as Kubernetes Secrets

#### Scenario: Re-run preserves existing secrets
- **WHEN** the script is re-run on a cluster that already has secrets
- **THEN** the script SHALL detect existing secrets and NOT overwrite them (idempotent behavior)

#### Scenario: Re-run updates Helm releases
- **WHEN** the script is re-run with updated chart values
- **THEN** the script SHALL use `helm upgrade --install` to update existing releases without data loss

### Requirement: Services deployment script
The system SHALL include an idempotent script `provision-services.sh` that deploys the application and runs load tests.

#### Scenario: Application deployment
- **WHEN** the script is run
- **THEN** the script SHALL build the application Docker image, deploy the modular monolith and API Gateway via `helm upgrade --install`, configure ingress, and wait for all pods to be ready

#### Scenario: Load test execution
- **WHEN** the application deployment completes successfully
- **THEN** the script SHALL execute k6 load testing scripts and report results

#### Scenario: Re-run updates deployment
- **WHEN** the script is re-run with a new application version
- **THEN** the script SHALL perform a rolling update of the application pods without downtime

### Requirement: Helm charts for application
The system SHALL include Helm charts for the modular monolith and API Gateway under `deploy/charts/`.

#### Scenario: Chart renders valid manifests
- **WHEN** `helm template` is run on the ticket-monster chart
- **THEN** the output SHALL include valid Deployment, Service, Ingress, and ConfigMap manifests

#### Scenario: Chart consumes infrastructure secrets
- **WHEN** the chart is deployed
- **THEN** the application pods SHALL consume database credentials and Redis/Redpanda connection strings from Kubernetes Secrets created by `provision-infra.sh` via `envFrom: secretRef`

#### Scenario: HPA configured
- **WHEN** the chart is deployed with HPA enabled
- **THEN** a HorizontalPodAutoscaler SHALL be created targeting CPU and memory utilization thresholds

### Requirement: k6 load testing scripts
The system SHALL include k6 scripts under `deploy/tests/k6/` that validate system behavior under load.

#### Scenario: Queue load test
- **WHEN** the queue load test script is executed
- **THEN** the script SHALL simulate 10,000 concurrent users joining the virtual queue and verify that all receive valid queue positions without errors

#### Scenario: Reservation under contention test
- **WHEN** the reservation contention test is executed
- **THEN** the script SHALL simulate 1,000 concurrent reservation requests for a zone with 100 tickets and verify that exactly 100 reservations succeed with zero overbooking

#### Scenario: End-to-end purchase flow test
- **WHEN** the end-to-end test is executed
- **THEN** the script SHALL simulate the full flow (join queue → get token → reserve → pay) for 500 concurrent users and verify successful ticket purchases
