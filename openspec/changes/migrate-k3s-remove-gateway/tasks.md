## 1. Limpiar `k3s-provision.sh`

- [x] 1.1 Eliminar despliegue "Hello World" nginx (deployment, service, ingress, middleware) del script
- [x] 1.2 Mantener solo: K3s, kubeconfig, Helm, k9s, cert-manager, ClusterIssuer Let's Encrypt

## 2. Crear `deploy/k3s/k3s-infrastructure.sh`

- [x] 2.1 Crear namespaces: `infrastructure`, `observability`, `ticket-monster` (skip si existen)
- [x] 2.2 Crear secrets en namespace `ticket-monster` con `openssl rand -hex 32` si no existen: `postgresql-credentials` (POSTGRES_PASSWORD), `mongodb-credentials` (MONGO_PASSWORD), `redis-credentials` (REDIS_PASSWORD), `keycloak-credentials` (KEYCLOAK_ADMIN_PASSWORD), `grafana-credentials` (GF_SECURITY_ADMIN_PASSWORD)
- [x] 2.3 Copiar secrets también al namespace `infrastructure` para que los Helm charts los lean
- [x] 2.4 Si los secrets ya existen, NO sobreescribirlos (idempotente)
- [x] 2.5 Desplegar PostgreSQL vía Helm (bitnami/postgresql) leyendo credenciales de `postgresql-credentials`
- [x] 2.6 Desplegar MongoDB vía Helm (bitnami/mongodb) leyendo credenciales de `mongodb-credentials`
- [x] 2.7 Desplegar Redis vía Helm (bitnami/redis) con `notify-keyspace-events=Ex`
- [x] 2.8 Desplegar Redpanda vía Helm (redpanda/redpanda)
- [x] 2.9 Desplegar Keycloak vía Helm (bitnami/keycloak) usando PostgreSQL existente, schema `keycloak`
- [x] 2.10 Desplegar observabilidad vía Helm: Prometheus (prometheus-community/prometheus), Loki (grafana/loki), Tempo (grafana/tempo), Grafana (grafana/grafana)
- [x] 2.11 Crear middlewares Traefik: `ticket-monster-rate-limit` (100 avg, 200 burst) y `ticket-monster-secure-headers` (X-Content-Type-Options, X-Frame-Options, HSTS)
- [x] 2.12 Todos los pasos deben ser idempotentes (skip si recurso/release ya existe)

## 3. Crear `deploy/k3s/k3s-app.sh`

- [x] 3.1 Construir imagen Docker del monolith desde `backend/ticketmonster/Dockerfile`
- [x] 3.2 Push a registry local K3s (`localhost:5000`) o a GHCR
- [x] 3.3 Helm upgrade --install `ticketmonster` en namespace `ticket-monster` con el chart `deploy/charts/ticketmonster/`
- [x] 3.4 Wait por pods Ready
- [x] 3.5 Smoke test con curl al health endpoint

## 4. Crear `deploy/k3s/deploy.sh`

- [x] 4.1 Script unificado que orqueste: k3s-provision → k3s-infrastructure → k3s-app
- [x] 4.2 Hacer ejecutable con `chmod +x`

## 5. Modificar Helm chart del monolith

- [x] 5.1 Cambiar `ingress.className` de `nginx` a `traefik` en `values.yaml`
- [x] 5.2 Cambiar `ingress.host` de `api.localhost` a `janrax.es` en `values.yaml`
- [x] 5.3 Añadir annotation de middlewares Traefik en `templates/ingress.yaml`
- [x] 5.4 Cambiar `image.repository` a `ghcr.io/jrgavilanes/ticket-monster` en `values.yaml`

## 6. Archivar el API Gateway

- [x] 6.1 Mover `deploy/charts/api-gateway/` a `deploy/charts/archive/api-gateway/`
- [x] 6.2 Eliminar referencias a `api-gateway` de `scripts/provision-services.sh`

## 7. GitHub Actions — Build y push del monolith a GHCR

- [x] 7.1 Crear `.github/workflows/docker-publish.yml` con trigger `workflow_dispatch` manual
- [x] 7.2 Aceptar input `version` (por defecto `latest`) para el tag de la imagen
- [x] 7.3 Login a GHCR con `${{ github.actor }}` y `${{ secrets.GITHUB_TOKEN }}`
- [x] 7.4 Build de la imagen desde `backend/ticketmonster/Dockerfile`
- [x] 7.5 Push a `ghcr.io/jrgavilanes/ticket-monster:${{ github.event.inputs.version }}`

## 8. Actualizar README

- [x] 8.1 Actualizar sección de despliegue con nuevas instrucciones (`deploy/k3s/deploy.sh`)
- [x] 8.2 Quitar referencias al API Gateway en el diagrama de arquitectura de despliegue
