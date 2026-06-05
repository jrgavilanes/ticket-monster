# Ticket Monster — Sistema de Reservaciones de Alta Concurrencia

## Objetivo Principal

El objetivo principal de este proyecto es la **documentación técnica y el análisis arquitectónico**, no el desarrollo puro. El foco está en:

- Documentar decisiones arquitectónicas y sus trade-offs
- Justificar cada elección tecnológica con argumentos sólidos
- Responder al cuestionario de evaluación con profundidad técnica
- Generar diagramas claros que comuniquen el diseño
- Demostrar comprensión de conceptos como DDD, CAP theorem, resiliencia, concurrencia y event-driven architecture

El código es secundario: sirve para validar que la arquitectura funciona, pero el entregable principal es la documentación.

## Descripción del Proyecto

Sistema en línea de venta de tickets para eventos de gran escala. Soporta diferentes venues, tipos de evento, fechas y artistas. Diseñado para 50M usuarios diarios activos (DAU) y capaz de soportar apertura de venta en eventos masivos (~5M usuarios concurrentes). El reto principal es garantizar que nunca haya overbooking.

## Scope (Requisitos Funcionales)

1. **Ver eventos** — Listar y consultar detalle de eventos disponibles
2. **Buscar eventos** — Búsqueda por nombre, artista, venue, fecha, tipo de evento
3. **Fila virtual** — Cola de acceso controlado para eventos de alta demanda
4. **Reservar y comprar tickets** — Reserva temporal (10 minutos) y compra de tickets de un evento

## Arquitectura

### Estilo: Monolito Modular Event-Driven (Spring Modulith)

Arquitectura basada en un monolito modular con comunicación híbrida (síncrona + asíncrona). Cada módulo mapea a un bounded context de DDD. La arquitectura está diseñada para poder extraer módulos a microservicios independientes cuando sea necesario, sin romper la aplicación.

### Bounded Contexts (Módulos DDD)

1. **Catalog Module** — Gestión de eventos, venues, artistas, fechas, tipos de evento y disponibilidad de asientos/zonas. Read-heavy.
2. **Virtual Queue Module** — Fila virtual para eventos masivos. Controla el acceso gradual de usuarios al sistema de compra mediante lotes.
3. **Reservation Module** — Reserva temporal de tickets con lock de 10 minutos. Aquí vive la lógica anti-overbooking.
4. **Payment Module** — Proceso de compra/checkout. Convierte una reserva en una venta confirmada.

### Comunicación entre Módulos

Modelo híbrido:

| Flujo | Tipo | Tecnología |
|---|---|---|
| Ver/Buscar eventos | Síncrono (GraphQL) | Llamada directa al Catalog Module |
| Entrar a fila virtual | Asíncrono (evento) | Redpanda |
| Reservar tickets | Síncrono (REST) | Llamada directa al Reservation Module |
| Confirmar compra | Asíncrono (evento) | Redpanda (Payment → Reservation) |
| Expiración de reserva | Asíncrono (evento/TTL) | Redis TTL + Redpanda |

### Seguridad

- **Keycloak** como Identity Provider (OAuth2 + OIDC), desplegado en K3s
- **API Gateway** (Spring Cloud Gateway) como punto de entrada único: valida JWTs, rate limiting (Resilience4j), routing
- Roles: `USER` (compra) y `ADMIN` (gestión de catálogo)
- El monolito modular valida JWTs con `spring-boot-starter-oauth2-resource-server`
- El token de turno de la fila virtual va como claim custom en el JWT o como token separado firmado por Queue Module

## Estrategia Anti-Overbooking

Reserva pesimista con lock distribuido:

1. Usuario solicita reservar N tickets de un evento
2. Reservation Module valida que el usuario no exceda el límite configurable de tickets por cliente (anti-fraude, ej: máximo 3)
3. Verifica disponibilidad en PostgreSQL (SELECT ... FOR UPDATE o atomic decrement)
4. Si hay stock, crea un lock en Redis con TTL de 10 minutos (`SET reservation:{eventId}:{seatId} {userId} EX 600`)
5. Se publica evento `reservation-created` en Redpanda
6. Si el usuario paga dentro de los 10 min → Payment Module publica `payment-confirmed` → Reservation Module convierte el lock en venta permanente
7. Si expira el TTL → Redis key expira → un listener publica `reservation-expired` → el stock se libera

Esto garantiza que nunca se vendan más tickets de los disponibles porque el lock es atómico y exclusivo.

## Fila Virtual

Cola FIFO en Redis con rate limiting por lotes:

1. Usuario quiere comprar → se encola en Redis con un ticket-id único
2. Un dispatcher libera lotes de N usuarios cada X segundos hacia el Reservation Module (configurable, ej: 500 usuarios cada 2 segundos)
3. El usuario consulta su posición en la cola vía polling o WebSocket
4. Cuando es su turno, recibe un token de acceso temporal (JWT con TTL corto) que le permite reservar
5. Si el token expira sin usar, se libera el slot para el siguiente en la cola

## Modelo de Datos y Bases de Datos

| Módulo | Base de Datos | Razón |
|---|---|---|
| Catalog | MongoDB | Esquema flexible (diferentes tipos de evento), read-heavy, fácil de escalar horizontalmente |
| Reservation | PostgreSQL | Strong consistency, transacciones ACID, SELECT FOR UPDATE |
| Payment | PostgreSQL | Transacciones financieras, ACID, auditoría, idempotencia |
| Virtual Queue | Redis (solo) | Cola en memoria, sin persistencia a disco. Si Redis cae, la cola se reconstruye |

### Entidades Principales

- **Catalog**: Venue, Event, Artist, EventDate, Zone/Section
- **Reservation**: Reservation, ReservationItem, TicketLock
- **Payment**: Payment, PaymentTransaction

## APIs

| Módulo | Estilo | Razón |
|---|---|---|
| Catalog | GraphQL (Spring for GraphQL) | Consultas flexibles, evita over-fetching, relaciones complejas |
| Virtual Queue | REST | Operaciones simples |
| Reservation | REST | Operaciones transaccionales |
| Payment | REST | Integración con pasarelas externas, webhooks |

### Endpoints Principales

**Catalog Module (GraphQL):**
- Query: `events`, `event(id)`, `searchEvents(query)`, `availability(eventId)`

**Virtual Queue Module (REST):**
- `POST /api/v1/queue/{eventId}/join` — Entrar a la fila
- `GET /api/v1/queue/{eventId}/status` — Consultar posición
- `GET /api/v1/queue/{eventId}/token` — Obtener token de acceso

**Reservation Module (REST):**
- `POST /api/v1/reservations` — Crear reserva (requiere queue token)
- `GET /api/v1/reservations/{id}` — Consultar reserva
- `DELETE /api/v1/reservations/{id}` — Cancelar reserva

**Payment Module (REST):**
- `POST /api/v1/payments` — Iniciar pago
- `GET /api/v1/payments/{id}` — Estado del pago
- `POST /api/v1/payments/{id}/confirm` — Confirmar (webhook de pasarela)

## Tech Stack

| Componente | Tecnología |
|---|---|
| Backend | Spring Boot + Spring Modulith |
| Event Streaming | Redpanda |
| Cache / Locks / Queue | Redis |
| Orquestador | K3s |
| DB relacional | PostgreSQL |
| DB documental | MongoDB |
| API Gateway | Spring Cloud Gateway |
| Auth | Keycloak (OAuth2 + OIDC) |
| API Catalog | Spring for GraphQL |
| Resiliencia | Resilience4j (circuit breaker, rate limiter, retry, timeout, bulkhead) |
| Observabilidad — Logs | Loki + Logback |
| Observabilidad — Metrics | Prometheus + Micrometer (Spring Boot Actuator) |
| Observabilidad — Traces | Tempo + OpenTelemetry Java Agent |
| Observabilidad — Dashboard | Grafana |
| Load Testing | k6 + oha |
| Despliegue | Helm charts + Docker |

## Resiliencia

- **Resilience4j** en el monolito modular: circuit breaker, rate limiter, retry, timeout, bulkhead
- Dead letter queues en Redpanda para eventos fallidos
- Idempotencia en Payment Module
- HPA (Horizontal Pod Autoscaler) en K3s para autoescalado bajo carga

## Despliegue

3 scripts de provisionamiento idempotentes:

### `provision-k3s.sh`
Configura remotamente un VPS Debian 12 desde cero:
- Instala K3s
- Instala k9s y Helm
- Configura kubeconfig local para acceso remoto
- Uso: `./provision-k3s.sh -u janrax@janrax.es -d janrax.es`

### `provision-infra.sh`
Despliega toda la infraestructura en K3s:
- cert-manager (ClusterIssuer Let's Encrypt, como servicio de K3s)
- Redpanda, Redis, PostgreSQL, MongoDB (Helm charts oficiales/Bitnami)
- Grafana stack (Prometheus, Loki, Tempo, Grafana)
- Genera secrets autogenerados con `openssl rand -hex 32` (idempotente: detecta si ya existen)
- Uso: `./provision-infra.sh -u janrax@janrax.es -d janrax.es`

### `provision-services.sh`
Instala/actualiza la aplicación y ejecuta tests:
- `helm upgrade --install` para el monolito modular (idempotente)
- Deploy del monolito modular + API Gateway + ingress
- Ejecuta tests de k6 al final
- Uso: `./provision-services.sh -u janrax@janrax.es -d janrax.es`

### Estructura de Deploy

```
deploy/
├── charts/
│   ├── ticket-monster/          # monolito modular
│   └── api-gateway/
├── infra/
│   ├── cert-manager/
│   ├── redpanda/
│   ├── redis/
│   ├── postgresql/
│   ├── mongodb/
│   └── observability/           # Grafana + Prometheus + Loki + Tempo
└── tests/
    └── k6/                      # scripts de load testing
```

La aplicación consume los secrets creados por `provision-infra.sh` vía `envFrom: secretRef`.

## Documentación

README profesional con diagramas Mermaid:

| Diagrama | Tipo Mermaid | Qué muestra |
|---|---|---|
| Arquitectura general | `flowchart` | Visión de alto nivel: usuarios, monolito modular, infra, comunicación |
| Context Map (DDD) | `flowchart` | Bounded contexts (módulos) y sus relaciones (sync/async) |
| Flujo de compra | `sequenceDiagram` | Usuario → Queue → Reservation → Payment, paso a paso |
| Anti-overbooking | `sequenceDiagram` | Flujo detallado del lock distribuido en Redis |
| Fila virtual | `flowchart` | Dispatcher, lotes, tokens, expiración |
| Despliegue | `flowchart` | K3s, namespaces, pods, ingress, monolito modular |

## Cuestionario de Evaluación (Respuestas Arquitectónicas)

### ¿Cómo está protegido el sistema contra overbooking?

Reserva pesimista con lock distribuido en Redis + atomic decrement en PostgreSQL. Antes de crear un lock, se valida que el usuario no exceda el límite configurable de tickets por cliente (anti-fraude). El lock en Redis es atómico y exclusivo (`SET NX EX 600`), y la verificación en PostgreSQL usa `SELECT FOR UPDATE` para garantizar consistencia a nivel de base de datos. Si el pago no se completa en 10 minutos, el TTL de Redis expira automáticamente y el stock se libera.

### ¿En qué partes del diseño se integran capacidades resilientes?

- **Resilience4j** en el monolito modular: circuit breaker (evita cascada de fallos), rate limiter (protege contra picos), retry (reintentos con backoff), timeout (evita bloqueos indefinidos), bulkhead (aisla recursos por módulo)
- **Dead letter queues** en Redpanda para eventos que fallan tras reintentos
- **Idempotencia** en Payment Module para evitar cobros duplicados
- **HPA** en K3s para autoescalado horizontal bajo carga
- **API Gateway** con rate limiting global

### ¿Cómo se usa la concurrencia en el sistema?

- **Locks distribuidos en Redis** (`SET NX EX`) para garantizar exclusividad en reservas
- **SELECT FOR UPDATE** en PostgreSQL para serializar acceso a inventario
- **Procesamiento concurrente de lotes** en Virtual Queue (dispatcher libera N usuarios en paralelo)
- **Event-driven con Redpanda** para procesamiento asíncrono concurrente entre módulos
- **HPA** en K3s escala réplicas de pods concurrentemente

### ¿Cómo se maneja la llegada masiva de usuarios en un evento extremadamente popular?

1. **Fila virtual** en Redis (FIFO) absorbe los 5M de usuarios concurrentes sin impactar los módulos de reserva
2. **Dispatcher por lotes** libera usuarios gradualmente (ej: 500 cada 2 segundos), controlando backpressure
3. **Token JWT de acceso temporal** limita el tiempo de acceso al sistema de reserva
4. **API Gateway** con rate limiting global protege el edge
5. **HPA** en K3s autoescala los pods del monolito modular según CPU/memoria
6. **Resilience4j** rate limiter y bulkhead protegen cada módulo individualmente

### Análisis desde la perspectiva del CAP Theorem

| Componente | Elección CAP | Razón |
|---|---|---|
| Reservation Module | **CP** (Consistencia + Partición) | No se permite overbooking. Se sacrifica disponibilidad para garantizar consistencia. |
| Payment Module | **CP** | Transacciones financieras requieren consistencia absoluta. |
| Catalog Module | **AP** (Disponibilidad + Partición) | Es read-heavy. Se tolera eventual consistency (datos ligeramente desactualizados) para mantener alta disponibilidad. |
| Virtual Queue | **AP** | Redis es eventualmente consistente. Perder la cola en caso de partición es un trade-off aceptable (se reconstruye). |
| Redpanda | **CP** | Usa Raft consensus para garantizar consistencia en el streaming de eventos. |

## Estrategia de Evolución: Monolito Modular → Microservicios

La arquitectura está diseñada siguiendo el principio "monolith first, microservices later" (Martin Fowler, Sam Newman):

1. **Fase inicial**: Monolito modular con Spring Modulith. Los bounded contexts son módulos internos con boundaries claros y comunicación desacoplada vía Redpanda.
2. **Ventajas**: Desarrollo más rápido, un solo deployable, debugging simplificado, tests de integración más fáciles.
3. **Spring Modulith** verifica automáticamente que no hay acoplamiento indebido entre módulos (tests de arquitectura).
4. **Extracción gradual**: Cuando un módulo necesite escalar independientemente o tenga diferentes requisitos de despliegue, se extrae a microservicio sin romper la aplicación porque la comunicación asíncrona ya está establecida.
5. **Criterio de extracción**: Se extrae un módulo a microservicio cuando:
   - Requiere escalado independiente (ej: Catalog es read-heavy y necesita más réplicas)
   - Tiene diferentes ciclos de release
   - Necesita tecnología específica (ej: Queue podría beneficiarse de un runtime diferente)
