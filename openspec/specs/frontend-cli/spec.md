## ADDED Requirements

### Requirement: Health check inicial
El script SHALL verificar que Keycloak y API Gateway responden antes de autenticar. Si no responden, SHALL mostrar un mensaje indicando que ejecute `docker compose --profile dev up -d` y SHALL salir con código 1.

#### Scenario: Health check exitoso
- **WHEN** el script se ejecuta y Keycloak (`:8180`) y Gateway (`:8080`) responden
- **THEN** el script continúa con la autenticación

#### Scenario: Health check fallido
- **WHEN** Keycloak o Gateway no responden
- **THEN** el script muestra "Error: Entorno no disponible. Ejecuta 'docker compose --profile dev up -d' primero" y sale con código 1

### Requirement: Autenticación vía Keycloak
El script SHALL obtener un token JWT vía `POST /realms/ticket-monster/protocol/openid-connect/token` con `grant_type=password`. SHALL extraer y guardar `access_token`, `refresh_token` y `expires_in`. SHALL calcular `expires_at` como timestamp actual + `expires_in`.

#### Scenario: Login exitoso
- **WHEN** el usuario ejecuta `./frontend.sh admin admin`
- **THEN** el script obtiene un access_token y refresh_token de Keycloak

#### Scenario: Login fallido
- **WHEN** las credenciales son incorrectas
- **THEN** el script muestra error de autenticación y sale con código 1

### Requirement: Detección de rol
El script SHALL decodificar el JWT (payload en base64) y leer `realm_access.roles`. Si contiene "ADMIN", el rol es admin. Si solo contiene "USER", el rol es user. SHALL mostrar el rol detectado al usuario.

#### Scenario: Admin detectado
- **WHEN** el token JWT contiene `realm_access.roles: ["USER","ADMIN"]`
- **THEN** el script muestra "Rol: ADMIN" y presenta el menú de administrador

#### Scenario: User detectado
- **WHEN** el token JWT contiene `realm_access.roles: ["USER"]`
- **THEN** el script muestra "Rol: USER" y presenta el menú de usuario

### Requirement: Refresh automático de token
El script SHALL renovar el token antes de cada acción si `expires_at - now < 30` segundos, usando `refresh_token`. Si una llamada HTTP devuelve 401, SHALL reintentar automáticamente con un refresh previo. Si el refresh_token también ha expirado, SHALL mostrar error y salir.

#### Scenario: Refresh preventivo
- **WHEN** faltan menos de 30s para que expire el access_token
- **THEN** el script renueva el token silenciosamente antes de ejecutar la acción

#### Scenario: Refresh reactivo
- **WHEN** una llamada a la API devuelve 401
- **THEN** el script renueva el token y reintenta la llamada una vez

#### Scenario: Refresh fallido
- **WHEN** el refresh_token ha expirado
- **THEN** el script muestra "Sesión expirada. Vuelve a ejecutar el script." y sale con código 1

### Requirement: Menú interactivo de administrador
El script SHALL mostrar un menú numerado con las siguientes opciones cuando el rol es ADMIN:
1. Crear Artista
2. Crear Venue
3. Crear Evento
4. Publicar Evento
5. Listar Eventos
6. Ver disponibilidad
7. Salir

SHALL leer la opción del usuario, ejecutar la acción correspondiente, mostrar el resultado y volver al menú.

#### Scenario: Crear Artista
- **WHEN** el admin elige opción 1, introduce nombre y género
- **THEN** el script ejecuta `mutation createArtist` vía GraphQL y muestra el ID del artista creado

#### Scenario: Crear Venue
- **WHEN** el admin elige opción 2, introduce nombre y capacidad total
- **THEN** el script ejecuta `mutation createVenue` vía GraphQL y muestra el ID del venue creado

#### Scenario: Crear Evento
- **WHEN** el admin elige opción 3, introduce nombre, tipo, fecha, venueId y zonas
- **THEN** el script ejecuta `mutation createEvent` vía GraphQL y muestra el ID del evento y de cada zona

#### Scenario: Publicar Evento
- **WHEN** el admin elige opción 4 e introduce un eventId
- **THEN** el script ejecuta `mutation updateEvent(status: PUBLISHED)` vía GraphQL

### Requirement: Menú interactivo de usuario
El script SHALL mostrar un menú numerado con las siguientes opciones cuando el rol es USER:
1. Listar Eventos
2. Ver disponibilidad
3. Comprar entradas
4. Pagar reserva
5. Ver reserva
6. Salir

#### Scenario: Listar Eventos
- **WHEN** el user elige opción 1
- **THEN** el script ejecuta `query events` vía GraphQL y muestra los eventos disponibles

#### Scenario: Comprar entradas (flujo completo)
- **WHEN** el user elige opción 3 e introduce eventId, zoneId y cantidad
- **THEN** el script ejecuta: join queue → polling cada 2s hasta TURN_READY → get token → create reservation. Muestra ticketId, posición, y reservationId al final.

### Requirement: Polling de cola virtual
El script SHALL hacer polling a `GET /queue/{eventId}/status` cada 2 segundos hasta obtener `TURN_READY`. SHALL mostrar "Esperando turno... posición: X" en cada intento. Si tras 60 intentos (2 min) no hay turno, SHALL preguntar si desea seguir esperando.

#### Scenario: Polling exitoso
- **WHEN** el usuario está en la cola y su turno llega
- **THEN** el script muestra "¡Turno asignado!" y continúa con obtener token

#### Scenario: Polling timeout
- **WHEN** pasan 60 intentos sin TURN_READY
- **THEN** el script pregunta "¿Seguir esperando? (s/n)" y actúa en consecuencia

### Requirement: Pago de reserva
El script SHALL permitir iniciar y confirmar un pago. SHALL pedir reservationId y amount, ejecutar `POST /payments`, mostrar paymentId, luego ejecutar `POST /payments/{id}/confirm` con idempotencyKey y mostrar resultado.

#### Scenario: Pago exitoso
- **WHEN** el usuario introduce reservationId y amount válidos
- **THEN** el script crea el pago, muestra paymentId, lo confirma y muestra estado CONFIRMED

### Requirement: Formato de salida JSON
El script SHALL usar `jq .` para formatear respuestas JSON si está instalado. Si no, SHALL mostrar el JSON crudo y mostrar un aviso al inicio (una vez) de que `jq` mejora la experiencia.

#### Scenario: jq instalado
- **WHEN** `jq` está disponible
- **THEN** todas las respuestas JSON se muestran con `jq .`

#### Scenario: jq no instalado
- **WHEN** `jq` no está disponible
- **THEN** el script muestra un aviso al inicio y las respuestas JSON se muestran sin formato

### Requirement: Configuración de URLs
El script SHALL definir `KEYCLOAK_URL` y `GATEWAY_URL` como variables editables al inicio del archivo, con valores por defecto `http://localhost:8180` y `http://localhost:8080` respectivamente.

#### Scenario: URLs por defecto
- **WHEN** el usuario no modifica el script
- **THEN** las URLs apuntan a localhost:8180 (Keycloak) y localhost:8080 (Gateway)

### Requirement: Documentación en README.md
El script SHALL estar documentado en el `README.md` raíz del proyecto. La documentación SHALL incluir: propósito del script, prerequisitos (curl, jq opcional), instrucciones de uso (`./frontend.sh <usuario> <password>`), tabla de usuarios de prueba, ejemplos de recorrido admin y user, y enlace al código fuente.

#### Scenario: README documenta el CLI
- **WHEN** un desarrollador lee el README.md
- **THEN** encuentra una sección "Frontend CLI" con propósito, prerequisitos, comandos de ejecución, usuarios de prueba y ejemplos de uso

#### Scenario: Ejemplo de recorrido admin
- **WHEN** un desarrollador sigue la documentación del CLI
- **THEN** encuentra un ejemplo de sesión admin: login como admin, crear artista, crear venue, crear evento, publicar evento

#### Scenario: Ejemplo de recorrido user
- **WHEN** un desarrollador sigue la documentación del CLI
- **THEN** encuentra un ejemplo de sesión user: login como user, listar eventos, comprar entradas (cola + reserva), pagar
