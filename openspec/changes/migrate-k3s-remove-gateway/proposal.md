## Why

El API Gateway Spring Cloud Gateway añade latencia extra, es un SPOF, y sufre problemas de concurrencia (405 bajo carga en `RoutePredicateHandlerMapping`). Como la arquitectura actual es un monolito, el gateway no aporta valor real de ruteo. Traefik (incluido en K3s) puede asumir rate limiting, circuit breaking y CORS como middlewares de infraestructura. Resilience4j —ya presente en el monolith— cubre la resiliencia interna. Eliminar el gateway simplifica el despliegue, reduce latencia y elimina el cuello de botella.

## What Changes

- **Deploy full infrastructure vía Helm** en el nuevo script `k3s-provision.sh`: PostgreSQL, MongoDB, Redis, Redpanda, Keycloak, + observabilidad (Prometheus, Loki, Tempo, Grafana)
- **Modificar Helm chart del monolith**: `ingressClassName: traefik`, entrada única sin subdominio (`janrax.es`), añadir middlewares Traefik (rate limit + secure headers)
- **Archivar API Gateway**: mover `deploy/charts/api-gateway/` a `archive/`. El código fuente permanece en el repo para referencia. No se compila ni despliega.
- **Crear script `deploy-app.sh`**: build Docker + push + Helm deploy del monolith
- **Crear script `deploy.sh`** unificado que orqueste: K3s → infra → app → smoke test
- **Actualizar specs OpenSpec** para reflejar el nuevo modelo sin gateway
- **Actualizar README** con las nuevas instrucciones de despliegue

## Capabilities

### New Capabilities

- `<none>`

### Modified Capabilities

- `deployment`: eliminar referencias a api-gateway, añadir middlewares Traefik, entrada única sin subdominio
- `api-gateway`: marcar como deprecated — archivado a favor de Traefik + monolith directo
- `gateway-routing`: deprecated — el ruteo intra-servicios no aplica en un monolito único
- `gateway-rate-limiting`: reemplazar API Gateway por Traefik Middleware RateLimit (mismos valores)
- `gateway-resilience`: reemplazar circuit breakers del gateway por Resilience4j existente en el monolith
- `gateway-security`: reemplazar validación JWT del gateway por Spring Security en el monolith
- `gateway-cors`: reemplazar CORS del gateway por middlewares de Traefik

## Impact

- `deploy/k3s/k3s-provision.sh`: script principal de provisioning (modificado)
- `deploy/k3s/deploy-app.sh`: nuevo script de despliegue de app
- `deploy/k3s/deploy.sh`: nuevo script unificado
- `deploy/k3s/middlewares/`: nuevos archivos YAML de middlewares Traefik
- `deploy/charts/ticketmonster/`: chart actualizado (Traefik, middlewares, host único)
- `deploy/charts/archive/api-gateway/`: chart archivado
- `scripts/provision-services.sh`: eliminar referencias a gateway
- `openspec/specs/deployment/spec.md`: actualizado
- `openspec/specs/api-gateway/`, `gateway-*/`: marcados como deprecated o modificados
- `README.md`: sección de despliegue actualizada
