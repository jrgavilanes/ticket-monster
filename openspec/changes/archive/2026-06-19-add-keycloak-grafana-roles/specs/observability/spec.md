## ADDED Requirements

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
