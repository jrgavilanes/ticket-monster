## Why

Todos los módulos del monólito (reservation, payment) y Keycloak comparten el schema `public` en la base de datos PostgreSQL `ticketmonster`. Esto mezcla tablas de distintos bounded contexts en un mismo namespace, dificulta la evolución independiente de cada módulo y complica una futura extracción a microservicios.

## What Changes

- Separar las tablas de cada módulo en schemas PostgreSQL dedicados (`reservation`, `payment`, `keycloak`)
- **BREAKING**: Las migraciones Flyway se reorganizan en ubicaciones por schema; requiere `docker compose down -v` para entornos existentes
- Unificar la configuración JPA en un solo DataSource compartido con schemas gestionados via `@Table(schema="...")` en las entidades
- Keycloak usa schema `keycloak` via `KC_DB_SCHEMA`

## Capabilities

### New Capabilities

- `db-schema-separation`: Cada bounded context (reservation, payment) y Keycloak usan un schema PostgreSQL independiente dentro de la misma base de datos, con migraciones Flyway separadas por módulo

### Modified Capabilities

- `ticket-reservation`: Las tablas `reservations`, `reservation_items`, `zone_stock` se mueven al schema `reservation`
- `payment-processing`: Las tablas `payments`, `payment_audit` se mueven al schema `payment`
- `local-development`: El script de init de PostgreSQL crea los schemas al arrancar; docker-compose expone el volumen de init
- `deployment`: Helm values y provision-infra.sh incluyen variables de schema para Keycloak y el monólito

## Impact

- **Entidades JPA**: `Reservation`, `ReservationItem`, `ZoneStock`, `Payment`, `PaymentAudit` añaden `@Table(schema="...")`
- **Flyway**: Dos instancias manuales (`reservationFlyway`, `paymentFlyway`), auto-config desactivado
- **DataSource**: Unificado en `DataSourceConfig.java`, eliminando configs separados por módulo
- **application.yml**: `ddl-auto: none`, `flyway.enabled: false`
- **docker-compose.yml**: Mount de `docker/postgres/init/` + `KC_DB_SCHEMA=keycloak`
- **Test integración**: Queries SQL con nombres cualificados (`reservation.zone_stock`, `payment.payments`)
- **Helm/K3s**: Variables `RESERVATION_SCHEMA`, `PAYMENT_SCHEMA`
