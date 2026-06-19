## Context

Grafana is configured with OAuth via Keycloak using the `generic_oauth` auth module. The current configuration grants Editor access by default to any authenticated user unless they have the `ADMIN` Keycloak realm role, which maps to Grafana Admin:

```
contains(realm_access.roles[*], 'ADMIN') && 'Admin' || 'Editor'
```

Keycloak's realm-export.json defines two realm roles (`USER`, `ADMIN`) and a `grafana` OIDC client. Both docker-compose (`docker/keycloak/realm-export.json`) and K3s Helm charts (`deploy/k3s/charts/keycloak/realm-export.json`) use identical realm exports.

## Goals / Non-Goals

**Goals:**
- Define three Keycloak realm roles: `grafana-viewer`, `grafana-editor`, `grafana-admin`
- Map roles to Grafana org roles via OAuth `role_attribute_path`
- Deny access to users without any `grafana-*` role
- Sync changes across both docker-compose (local dev) and K3s (production)

**Non-Goals:**
- Client-level roles (realm roles suffice)
- Grafana folder-level or team-level permissions
- Keycloak group-based role inheritance
- Dynamic role assignment via user attributes

## Decisions

### Realm roles over client roles

Realm roles are simpler to manage and appear in the `realm_access.roles` claim by default. Client roles would require additional scope mapping on the `grafana` client. Since these roles are general access-control constructs (not service-specific permissions), realm roles are the natural fit.

### Role hierarchy via JMESPath `contains`

Grafana's `role_attribute_path` uses JMESPath to derive the Grafana org role from the OIDC claims. The chosen expression:

```
contains(realm_access.roles[*], 'grafana-admin') && 'Admin' || contains(realm_access.roles[*], 'grafana-editor') && 'Editor' || contains(realm_access.roles[*], 'grafana-viewer') && 'Viewer' || 'Deny'
```

- `'Deny'` as the fallback returns an invalid role, causing Grafana to reject the login. This ensures only explicitly authorized users can access Grafana.
- Hierarchy: Admin > Editor > Viewer. A user with both `grafana-admin` and `grafana-editor` gets Admin (first match wins).
- Alternative considered: `role_attribute_strict = true` with an `org_mapping` configmap — rejected because it requires managing an additional configuration file and is no clearer than the JMESPath expression.

### User role assignments

- `admin` user gets `grafana-admin` (can manage datasources, users, dashboards)
- `user` user gets `grafana-viewer` (read-only access, minimum required)
- Both users retain their existing `USER`/`ADMIN` realm roles for backwards compatibility

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| Realm import fails due to incompatible schema | Backup existing realm; the new roles are purely additive |
| Existing users lose Grafana access after role mapping change | Assign `grafana-viewer` as a default minimum role to existing users |
| K3s realm-export drifts from docker | Both files are updated in this change and stored in version control |
| Admin locked out if JMESPath expression is malformed | Use local Grafana admin user (`GF_SECURITY_ADMIN_USER`) as emergency fallback |

## Open Questions

- None at this stage. The role mapping is straightforward and both environments use identical Keycloak configurations.
