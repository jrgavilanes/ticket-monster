## MODIFIED Requirements

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
