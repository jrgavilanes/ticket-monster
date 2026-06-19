## Why

Currently, any Keycloak user who can authenticate receives Grafana Editor access by default. This poses a security risk: all authenticated users can modify dashboards and datasources. We need role-based access control so only users explicitly granted Grafana roles can access the monitoring stack, with differentiated permissions (Viewer, Editor, Admin).

## What Changes

- Add three Keycloak realm roles: `grafana-viewer`, `grafana-editor`, `grafana-admin`
- Assign `grafana-admin` to the existing `admin` user
- Update Grafana's OAuth role attribute path to map Keycloak roles to Grafana roles (`grafana-admin` → Admin, `grafana-editor` → Editor, `grafana-viewer` → Viewer)
- Users without any grafana-* role SHALL be denied access to Grafana
- Update both docker-compose and K3s Grafana OAuth configuration
- Sync both realm-export.json files (docker/ and deploy/k3s/)

## Capabilities

### New Capabilities
- `keycloak-grafana-roles`: Keycloak realm roles and Grafana OAuth role mapping for tiered access control to Grafana

### Modified Capabilities
- `observability`: Grafana OAuth role mapping changes from single ADMIN check to hierarchical role resolution (`grafana-admin`, `grafana-editor`, `grafana-viewer`)

## Impact

- **Keycloak**: realm-export.json files (docker/ and deploy/k3s/) — new realm roles, user role assignments
- **Grafana**: docker-compose.yml (`GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH`), k3s chart values.yaml (`auth.oauthRoleAttributePath`)
- **Users**: Existing `admin` user gains `grafana-admin`; `user` user needs a grafana-* role assigned to access Grafana
