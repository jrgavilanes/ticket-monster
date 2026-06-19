## Context

Actualmente el despliegue K3s tiene dos servicios expuestos via Ingress: `api.janrax.es` (monolith) y `gateway.janrax.es` (API Gateway). El gateway añade un hop extra + latencia y sufre problemas de concurrencia (`RoutePredicateHandlerMapping` devuelve 405 bajo carga). La arquitectura es un monolito único con Spring Modulith — no hay múltiples servicios que rutear.

El nuevo script `deploy/k3s/k3s-provision.sh` ya instala K3s con Traefik activado por defecto. Los scripts antiguos en `scripts/` usan `ingressClassName: nginx` con Traefik deshabilitado.

## Goals / Non-Goals

**Goals:**
- Eliminar el API Gateway del despliegue en producción
- Usar Traefik (incluido en K3s) como único edge con middlewares para rate limiting, headers de seguridad y CORS
- Delegar la resiliencia (circuit breakers, auth JWT) al monolith con Resilience4j y Spring Security
- Mantener la observabilidad (Prometheus, Loki, Tempo, Grafana) funcional
- Entrada única sin subdominios: `janrax.es` → monolith
- Scripts de provisioning idempotentes (skip si ya existe)

**Non-Goals:**
- Cambiar la arquitectura del monolith (seguirá siendo Spring Modulith)
- Modificar los módulos de negocio (Catalog, Queue, Reservation, Payment)
- Extraer microservicios del monolith
- Eliminar el código fuente del API Gateway del repo

## Decisions

### 1. Traefik como edge en lugar de API Gateway
**Alternativa**: Mantener el API Gateway. **Razón**: Traefik viene incluido en K3s, no requiere despliegue extra, contiene middlewares nativos para rate limiting y headers de seguridad, y elimina el SPOF del gateway. La otra alternativa (nginx ingress) requeriría instalar un controlador adicional.

### 2. Rate limiting en Traefik (no en el monolith)
**Alternativa**: Rate limiting con Resilience4j en el monolith. **Razón**: El rate limiting es una concern de infraestructura/edge. Traefik puede rechazar requests antes de que lleguen al monolith, protegiendo mejor contra DDoS. Se configuran los mismos valores: 100 req/s promedio, 200 burst.

### 3. Circuit breakers con Resilience4j en el monolith
**Alternativa**: Circuit breakers en Traefik. **Razón**: Traefik puede hacer circuit breaking a nivel HTTP, pero Resilience4j ya está en el monolith y puede hacer circuit breaking más granular (por dependencia: Redis, DB, Kafka) con half-open recovery. Además Resilience4j forma parte del stack del proyecto.

### 4. Auth JWT con Spring Security en el monolith
**Alternativa**: ForwardAuth de Traefik delegando a Keycloak. **Razón**: Spring Security ya valida JWT correctamente en el monolith (`oauth2ResourceServer.jwt()`). Mover la validación a Traefik añadiría complejidad de configuración y no aporta ventajas en un monolito único.

### 5. Entrada única sin subdominio de gateway
Se unifica todo bajo `janrax.es`. El API Gateway desaparece y `api.janrax.es` / `gateway.janrax.es` dejan de existir. El ingress del monolith apunta a la raíz.

### 6. Chart del gateway archivado, no eliminado
El chart `deploy/charts/api-gateway/` se mueve a `deploy/charts/archive/api-gateway/`. El código fuente en `backend/api-gateway/` se mantiene intacto. Esto permite recuperarlo si en el futuro se extraen microservicios.

## Risks / Trade-offs

- **Sin gateway centralizado para auth**: Si se extraen microservicios en el futuro, habrá que reintroducir un gateway o configurar auth en cada servicio. **Mitigación**: El código del gateway permanece en el repo listo para ser reactivado.
- **Traefik rate limiting no es distribuido**: El `RateLimit` middleware de Traefik es por-instancia. Con varios pods de Traefik, los límites se aplican por pod, no globalmente. **Mitigación**: Para el deployment actual (single-node K3s), Traefik tiene una sola instancia. Si se escala a multi-nodo, se podría usar `RateLimit` con Redis backend o reintroducir un gateway con Redis-based rate limiting.
- **Scripts de provisioning antiguos inconsistentes**: Los scripts en `scripts/` usan `nginx` y deshabilitan Traefik. **Mitigación**: El nuevo script `deploy/k3s/k3s-provision.sh` reemplaza completamente el flujo antiguo. Los scripts viejos se marcan como legacy en comentarios.
