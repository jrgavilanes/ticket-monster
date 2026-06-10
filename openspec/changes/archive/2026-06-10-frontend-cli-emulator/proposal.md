## Why

No existe un cliente CLI que permita a desarrolladores y testers interactuar con la API de Ticket Monster de forma rápida e interactiva. Actualmente hay que escribir curl manualmente cada vez. Un emulador de frontend en bash acelerará el desarrollo, testing y demostraciones al automatizar los flujos completos de administración y compra.

## What Changes

- Nuevo script `frontend/frontend.sh` que funciona como emulador CLI interactivo
- Documentación del CLI en `README.md` con instrucciones de uso, usuarios de prueba y ejemplos de recorrido
- Autenticación vía Keycloak OIDC password grant con refresh automático de token
- Menú interactivo para administradores: crear artistas, venues, eventos, publicar, listar, ver disponibilidad
- Menú interactivo para usuarios: listar eventos, ver disponibilidad, flujo completo de compra (cola virtual → reserva → pago)
- Health check inicial de Keycloak y API Gateway
- Detección automática de rol administrador vs usuario desde el JWT

## Capabilities

### New Capabilities
- `frontend-cli`: Emulador CLI interactivo para administradores y usuarios de Ticket Monster

### Modified Capabilities

<!-- No existing specs change - this is a new tool that consumes existing APIs -->

## Impact

- Nuevo directorio `frontend/` con `frontend.sh`
- No afecta al backend, API, ni infraestructura existente
- Dependencia funcional: `curl`, `jq` (opcional para pretty-print)
