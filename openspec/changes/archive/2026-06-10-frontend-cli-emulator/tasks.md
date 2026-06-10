## 1. Scaffold del script

- [x] 1.1 Crear `frontend/frontend.sh` con shebang `#!/usr/bin/env bash`, `set -euo pipefail` y permisos ejecutables
- [x] 1.2 Definir variables configurables al inicio: `KEYCLOAK_URL`, `GATEWAY_URL`, `CLIENT_ID`
- [x] 1.3 Detectar si `jq` está instalado y mostrar aviso si no

## 2. Health check y autenticación

- [x] 2.1 Implementar health check: verificar Keycloak (`:8180`) y Gateway (`:8080`) con `curl -sf`
- [x] 2.2 Implementar login: `POST /realms/ticket-monster/protocol/openid-connect/token`, extraer `access_token`, `refresh_token`, `expires_in` con `jq`
- [x] 2.3 Implementar función `refresh_token()` que renueve usando `refresh_token` y actualice las variables
- [x] 2.4 Implementar función `ensure_token()` que refresque si `expires_at - now < 30`
- [x] 2.5 Implementar función `api_call()` que ejecute curl con `ensure_token` y reintente una vez en 401 con refresh
- [x] 2.6 Implementar detección de rol: decodificar payload JWT (base64), leer `realm_access.roles`

## 3. Utilidades de menú

- [x] 3.1 Implementar función `mostrar_json()` que use `jq` si está disponible, o `cat` si no
- [x] 3.2 Implementar función `leer_input()` con mensaje y validación de vacío
- [x] 3.3 Implementar función `menu_admin()` con opciones 1-7 en bucle hasta "Salir"
- [x] 3.4 Implementar función `menu_user()` con opciones 1-6 en bucle hasta "Salir"

## 4. Acciones de administrador (GraphQL mutations)

- [x] 4.1 Implementar `crear_artista()`: pedir name, genre, llamar `mutation createArtist`
- [x] 4.2 Implementar `crear_venue()`: pedir name, totalCapacity, llamar `mutation createVenue`
- [x] 4.3 Implementar `crear_evento()`: pedir name, type, date, venueId, zonas, llamar `mutation createEvent`
- [x] 4.4 Implementar `publicar_evento()`: pedir eventId, llamar `mutation updateEvent(status: PUBLISHED)`
- [x] 4.5 Implementar `listar_eventos()`: llamar `query events` y mostrar en formato legible
- [x] 4.6 Implementar `ver_disponibilidad()`: pedir eventId, llamar `query availability`

## 5. Acciones de usuario (queue, reservation, payment)

- [x] 5.1 Implementar `listar_eventos_user()`: llamar `query events` (público)
- [x] 5.2 Implementar `ver_disponibilidad_user()`: pedir eventId, llamar `query availability`
- [x] 5.3 Implementar `comprar_entradas()`: join queue → polling cada 2s hasta TURN_READY → get token → pedir zoneId/cantidad → create reservation
- [x] 5.4 Implementar polling con timeout: 60 intentos (2 min), preguntar si seguir
- [x] 5.5 Implementar `pagar_reserva()`: pedir reservationId y amount → create payment → confirm payment con idempotencyKey
- [x] 5.6 Implementar `ver_reserva()`: pedir reservationId, llamar `GET /reservations/{id}`

## 6. Documentación en README.md

- [x] 6.1 Añadir sección "Frontend CLI" en `README.md` con propósito, prerequisitos (curl, jq opcional) e instrucciones de ejecución
- [x] 6.2 Documentar usuarios de prueba (admin/admin y user/user) y tabla de roles
- [x] 6.3 Añadir ejemplo de sesión admin: login → crear artista → crear venue → crear evento → publicar
- [x] 6.4 Añadir ejemplo de sesión user: login → listar eventos → comprar entradas → pagar reserva
- [x] 6.5 Incluir nota sobre URLs configurables y health check automático

## 7. Correcciones en backend (ReservationController)

- [x] 7.1 Añadir `@Transactional(readOnly = true)` en `ReservationController.getReservation()` para evitar `LazyInitializationException` al serializar `items`


## 8. Punto de entrada principal

- [x] 8.1 Validar argumentos (2 requeridos: usuario y password)
- [x] 8.2 Ejecutar health check
- [x] 8.3 Ejecutar login
- [x] 8.4 Detectar rol y mostrar menú correspondiente
- [x] 8.5 Manejar errores de conexión, autenticación y refresh expirado con mensajes claros
