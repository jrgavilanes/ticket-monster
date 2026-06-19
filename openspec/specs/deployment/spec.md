## ADDED Requirements

### Requirement: K3s cluster provisioning script
The system SHALL include an idempotent script `deploy/k3s/k3s-provision.sh` that provisions a Debian 12 VPS with K3s (Traefik enabled), k9s, Helm, cert-manager, and a Let's Encrypt ClusterIssuer. The script SHALL NOT deploy infrastructure or application components.

#### Scenario: Fresh VPS provisioning
- **WHEN** the script is run against a fresh Debian 12 VPS (`./k3s-provision.sh janrax@host janrax.es`)
- **THEN** the script SHALL install K3s with Traefik, configure kubeconfig, install Helm and k9s, deploy cert-manager, and create a Let's Encrypt ClusterIssuer

#### Scenario: Re-run is idempotent
- **WHEN** the script is re-run on an already provisioned VPS
- **THEN** the script SHALL skip existing components (K3s, cert-manager, Helm) and exit successfully

### Requirement: Infrastructure provisioning script
The system SHALL include an idempotent script `deploy/k3s/k3s-infrastructure.sh` that deploys all infrastructure components and the observability stack to the K3s cluster via Helm.

#### Scenario: Secrets auto-generation
- **WHEN** the script is run for the first time
- **THEN** the script SHALL generate secrets (`postgresql-credentials`, `mongodb-credentials`, `redis-credentials`, `keycloak-credentials`, `grafana-credentials`) using `openssl rand -hex 32` and store them as Kubernetes Secrets in the `ticket-monster` and `infrastructure` namespaces

#### Scenario: Re-run preserves existing secrets
- **WHEN** the script is re-run on a cluster that already has secrets
- **THEN** the script SHALL detect existing secrets and NOT overwrite them

#### Scenario: Infrastructure deployment
- **WHEN** the script is run
- **THEN** the script SHALL create namespaces (`infrastructure`, `observability`, `ticket-monster`) and deploy PostgreSQL, MongoDB, Redis, Redpanda, and Keycloak via Helm charts reading credentials from Kubernetes Secrets

#### Scenario: Observability deployment
- **WHEN** the script is run
- **THEN** the script SHALL deploy Prometheus, Loki, Tempo, and Grafana via Helm charts in the `observability` namespace

#### Scenario: Re-run updates Helm releases
- **WHEN** the script is re-run with updated chart values
- **THEN** the script SHALL use `helm upgrade --install` to update existing releases without data loss

### Requirement: Traefik RateLimit middleware
The system SHALL deploy a Traefik Middleware CRD named `ticket-monster-rate-limit` in the `ticket-monster` namespace with rate limiting configured at 100 requests per second average with 200 burst.

#### Scenario: Middleware is created by k3s-infrastructure.sh
- **WHEN** `k3s-infrastructure.sh` is executed
- **THEN** the middleware `ticket-monster-rate-limit` SHALL be created if it does not exist, and skipped if already present

#### Scenario: Middleware restricts excessive traffic
- **WHEN** a client exceeds 100 requests per second on average
- **THEN** Traefik SHALL reject excess requests with HTTP 429 Too Many Requests

### Requirement: Traefik secure headers middleware
The system SHALL deploy a Traefik Middleware CRD named `ticket-monster-secure-headers` in the `ticket-monster` namespace with security headers including `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `X-XSS-Protection: 1; mode=block`, and `Strict-Transport-Security`.

#### Scenario: Middleware is created by k3s-infrastructure.sh
- **WHEN** `k3s-infrastructure.sh` is executed
- **THEN** the middleware `ticket-monster-secure-headers` SHALL be created if it does not exist, and skipped if already present

#### Scenario: Response includes security headers
- **WHEN** any request passes through the middleware
- **THEN** the response SHALL include the configured security headers

### Requirement: Single entry point ingress without gateway subdomain
The monolith Helm chart SHALL configure a single Ingress at the root domain (`janrax.es`) with Traefik as the ingress class and TLS via cert-manager.

#### Scenario: Traffic routes directly to monolith
- **WHEN** a request is sent to `https://janrax.es/graphql`
- **THEN** the ingress SHALL route it directly to the ticketmonster service without passing through an API Gateway

### Requirement: Unified deployment script
The system SHALL include a script `deploy/k3s/deploy.sh` that orchestrates the full deployment: K3s provisioning, infrastructure deployment, application deployment, and smoke test.

#### Scenario: Full deployment from scratch
- **WHEN** `deploy.sh` is run against a clean VPS
- **THEN** it SHALL execute `k3s-provision.sh`, `k3s-infrastructure.sh`, `k3s-app.sh`, wait for all pods to be ready, and run a health check

### Requirement: GitHub Actions Docker build and push
The system SHALL include a GitHub Actions workflow `.github/workflows/docker-publish.yml` that builds the monolith Docker image and pushes it to GitHub Container Registry (ghcr.io).

#### Scenario: Manual trigger with custom version
- **WHEN** the workflow is triggered via `workflow_dispatch` with a version input (e.g. `1.0.0`)
- **THEN** it SHALL build the image from `backend/ticketmonster/Dockerfile` and push to `ghcr.io/jrgavilanes/ticket-monster:1.0.0`

#### Scenario: Manual trigger with default version
- **WHEN** the workflow is triggered via `workflow_dispatch` without specifying a version
- **THEN** it SHALL push the image with tag `latest`

### Requirement: Services deployment script
The system SHALL include an idempotent script `deploy/k3s/k3s-app.sh` that builds the application Docker image, pushes to the GitHub Container Registry, and deploys the monolith via Helm. The script SHALL NOT deploy an API Gateway.

#### Scenario: Application deployment
- **WHEN** the script is run
- **THEN** the script SHALL build the monolith Docker image, push it to `ghcr.io/jrgavilanes/ticket-monster`, deploy the monolith via `helm upgrade --install ticketmonster`, and wait for all pods to be ready

#### Scenario: Re-run updates deployment
- **WHEN** the script is re-run with a new application version
- **THEN** the script SHALL perform a rolling update of the application pods without downtime

### Requirement: Helm charts for application
The system SHALL include a Helm chart for the modular monolith under `deploy/charts/ticketmonster/` with the image pointing to `ghcr.io/jrgavilanes/ticket-monster`. The API Gateway chart SHALL be archived at `deploy/charts/archive/api-gateway/`.

#### Scenario: Chart renders valid manifests
- **WHEN** `helm template` is run on the ticket-monster chart
- **THEN** the output SHALL include valid Deployment, Service, Ingress with Traefik annotations, ConfigMap, and HPA manifests

#### Scenario: Ingress uses Traefik with middlewares
- **WHEN** the chart is deployed
- **THEN** the Ingress SHALL have `ingressClassName: traefik` and include annotations for `ticket-monster-rate-limit` and `ticket-monster-secure-headers` middlewares

#### Scenario: Chart uses GitHub Container Registry image
- **WHEN** the chart is deployed
- **THEN** the Deployment SHALL pull the image from `ghcr.io/jrgavilanes/ticket-monster:latest`

#### Scenario: Chart consumes infrastructure secrets
- **WHEN** the chart is deployed
- **THEN** the application pods SHALL consume database credentials and Redis/Redpanda connection strings from Kubernetes Secrets created by `k3s-infrastructure.sh` via `envFrom: secretRef`

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

### Requirement: Schema environment variables for monolith deployment
The monolith Helm chart SHALL expose `RESERVATION_SCHEMA` and `PAYMENT_SCHEMA` environment variables to the application container.

#### Scenario: Schema variables in Helm values
- **WHEN** the ticketmonster Helm chart is deployed
- **THEN** `RESERVATION_SCHEMA=reservation` and `PAYMENT_SCHEMA=payment` are passed as environment variables

### Requirement: Keycloak uses ticketmonster database with keycloak schema
The Keycloak K3s deployment SHALL connect to the `ticketmonster` database using schema `keycloak` instead of a separate `keycloak` database.

#### Scenario: Keycloak Helm values updated
- **WHEN** `provision-infra.sh` deploys Keycloak
- **THEN** `externalDatabase.database=ticketmonster` and `externalDatabase.schema=keycloak` are set
