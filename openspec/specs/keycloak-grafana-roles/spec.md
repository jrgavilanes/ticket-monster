## ADDED Requirements

### Requirement: Grafana access roles exist in Keycloak
The Keycloak realm SHALL define three realm roles for tiered Grafana access: `grafana-viewer`, `grafana-editor`, and `grafana-admin`.

#### Scenario: Roles are present in realm configuration
- **WHEN** Keycloak imports the realm-export.json at startup
- **THEN** the `grafana-viewer`, `grafana-editor`, and `grafana-admin` roles SHALL exist as realm roles

#### Scenario: Admin user has grafana-admin role
- **WHEN** the realm is imported
- **THEN** the `admin` user SHALL have the `grafana-admin` realm role assigned

#### Scenario: Regular user has grafana-viewer role
- **WHEN** the realm is imported
- **THEN** the `user` user SHALL have the `grafana-viewer` realm role assigned

### Requirement: Grafana OAuth role attribute path resolves roles hierarchically
Grafana's OAuth configuration SHALL map Keycloak realm roles to Grafana org roles using a JMESPath expression that enforces a hierarchy and denies access when no role is present.

#### Scenario: User with grafana-admin role gets Admin access
- **WHEN** a user with the Keycloak role `grafana-admin` authenticates with Grafana via OAuth
- **THEN** Grafana SHALL assign the org role `Admin`

#### Scenario: User with grafana-editor role gets Editor access
- **WHEN** a user with the Keycloak role `grafana-editor` (but not `grafana-admin`) authenticates
- **THEN** Grafana SHALL assign the org role `Editor`

#### Scenario: User with grafana-viewer role gets Viewer access
- **WHEN** a user with the Keycloak role `grafana-viewer` (but not `grafana-editor` or `grafana-admin`) authenticates
- **THEN** Grafana SHALL assign the org role `Viewer`

#### Scenario: User with multiple grafana roles gets highest role
- **WHEN** a user with both `grafana-viewer` and `grafana-admin` roles authenticates
- **THEN** Grafana SHALL assign the org role `Admin` (first match in the hierarchy wins)

#### Scenario: User without any grafana role is denied access
- **WHEN** a user without any of `grafana-viewer`, `grafana-editor`, or `grafana-admin` authenticates
- **THEN** Grafana SHALL reject the login attempt

### Requirement: Both environments share identical role configuration
The Keycloak realm configuration and Grafana OAuth settings SHALL be consistent between the docker-compose (local development) and K3s (production) environments.

#### Scenario: Docker realm-export matches roles
- **WHEN** the local docker-compose environment starts with `docker compose --profile infra up`
- **THEN** Keycloak SHALL contain `grafana-viewer`, `grafana-editor`, and `grafana-admin` realm roles

#### Scenario: K3s realm-export matches roles
- **WHEN** `provision-infra.sh` deploys Keycloak to K3s
- **THEN** Keycloak SHALL contain `grafana-viewer`, `grafana-editor`, and `grafana-admin` realm roles

#### Scenario: Grafana OAuth role mapping is consistent
- **WHEN** comparing docker-compose `GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH` with K3s `auth.oauthRoleAttributePath`
- **THEN** both SHALL use the same JMESPath expression resolving `grafana-*` roles
