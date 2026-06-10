## Context

Ticket Monster no tiene un frontend CLI. Los desarrolladores y testers dependen de comandos curl ad-hoc para interactuar con la API. Esto ralentiza el desarrollo, testing y demostraciones. Se necesita un script bash interactivo que emule los flujos de administrador y usuario final.

El script será puramente cliente — no requiere cambios en backend, API, ni infraestructura. Consume endpoints existentes: GraphQL (catalog) y REST (queue, reservations, payments) a través del API Gateway en `:8080`.

## Goals / Non-Goals

**Goals:**
- Script bash ejecutable `frontend/frontend.sh` que reciba usuario y contraseña como argumentos
- Health check inicial de Keycloak (`:8180`) y API Gateway (`:8080`)
- Autenticación OIDC password grant con refresh automático de token (híbrido: preventivo + reactivo)
- Detección de rol admin/user desde el JWT (`realm_access.roles`)
- Menú interactivo separado por rol
- Admin: CRUD de artistas, venues, eventos + publicación + listado + disponibilidad
- User: listado, disponibilidad, flujo completo de compra (cola → reserva → pago)
- Mostrar respuestas JSON con `jq` si disponible, raw si no

**Non-Goals:**
- No sustituye a un frontend web/móvil real
- No implementa lógica de negocio ni validaciones del lado cliente
- No persiste estado entre ejecuciones
- No soporta refresh_token expirado (si expira, el usuario debe reloguear)

## Decisions

1. **Puerto**: API Gateway (`:8080`) en lugar de monolith directo (`:8082`). Es el punto de entrada oficial con circuit breakers.
2. **Modo interactivo**: Menú con opciones numeradas, no subcomandos. Más amigable para recorridos completos.
3. **Detección de rol**: Decodificar JWT (base64) localmente. No requiere llamada extra. Claim: `realm_access.roles`.
4. **Token refresh híbrido**: Guardar `expires_at = now + expires_in`. Antes de cada acción, si expires_at - now < 30s, refrescar. Si una llamada da 401, reintentar con refresh automático.
5. **Flujo de compra unificado**: Join queue + polling automático cada 2s + get token + crear reserva en una sola opción del menú. Pago es opción separada.
6. **Polling automático**: Muestra "Esperando turno... posición: X" cada 2s. Límite de 60 intentos (2 min) antes de preguntar si continuar.
7. **URLs configurables**: Variables al inicio del script (`KEYCLOAK_URL`, `GATEWAY_URL`).
8. **Sin dependencias externas**: Solo `curl`. `jq` es opcional para pretty-print.

## Risks / Trade-offs

- **[bash complexity]** Los menús interactivos en bash requieren manejo cuidadoso de lectura de input y bucles. → Usar `select` o `read` con case. Validar entradas vacías.
- **[token expirado]** Si refresh_token también caduca (Keycloak default 30min), el usuario debe reloguear. → Mostrar mensaje claro y salir; sugerir volver a ejecutar.
- **[polling infinito]** En desarrollo, la cola virtual podría no avanzar. → Timeout de 2 min con pregunta al usuario.
- **[IDs como strings largos]** Los UUIDs de Keycloak son difíciles de copiar/pegar. → Mostrar IDs en línea separada y destacada.
