## 1. Keycloak Realm Roles

- [x] 1.1 Add `grafana-viewer`, `grafana-editor`, `grafana-admin` realm roles to `docker/keycloak/realm-export.json`
- [x] 1.2 Assign `grafana-admin` role to the `admin` user in `docker/keycloak/realm-export.json`
- [x] 1.3 Assign `grafana-viewer` role to the `user` user in `docker/keycloak/realm-export.json`
- [x] 1.4 Sync the same role changes to `deploy/k3s/charts/keycloak/realm-export.json`

## 2. Grafana OAuth Role Mapping

- [x] 2.1 Update `GF_AUTH_GENERIC_OAUTH_ROLE_ATTRIBUTE_PATH` in `docker-compose.yml` to use hierarchical `grafana-*` role mapping with `'Deny'` fallback
- [x] 2.2 Update `auth.oauthRoleAttributePath` in `deploy/k3s/charts/grafana/values.yaml` to match the same expression

## 3. Verification

- [x] 3.1 Verify JSON validity of both realm-export.json files (`python3 -m json.tool` or `jq`)
- [ ] 3.2 Verify `docker compose --profile infra up -d` starts successfully and Grafana OAuth login works with the configured roles
