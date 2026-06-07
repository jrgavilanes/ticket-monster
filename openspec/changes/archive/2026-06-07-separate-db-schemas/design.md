## Context

El monólito Spring Modulith usa una sola base de datos PostgreSQL (`ticketmonster`) con todas las tablas en el schema `public`. Los bounded contexts `reservation` y `payment` comparten este namespace, al igual que Keycloak. La arquitectura actual es:

```
Database: ticketmonster
└── public (todo mezclado)
    ├── zone_stock, reservations, reservation_items (reservation)
    ├── payments, payment_audit (payment)
    └── tablas de Keycloak
```

**Constraints**: No se puede usar `@Transactional(transactionManager="...")` con múltiples `PlatformTransactionManager` en Spring Boot 4.0.6 — el qualifier es ignorado y siempre se usa el `@Primary`.

## Goals / Non-Goals

**Goals:**
- Cada bounded context (reservation, payment) tiene su propio schema PostgreSQL
- Keycloak usa un schema dedicado (`keycloak`)
- Las migraciones Flyway se ejecutan por schema independientemente
- El cambio es transparente para la lógica de negocio (servicios y controladores no cambian)

**Non-Goals:**
- No se crean bases de datos separadas (sigue siendo una sola DB)
- No se implementan transacciones distribuidas entre módulos
- No se modifica la API REST ni GraphQL

## Decisions

### 1. Un solo DataSource + `@Table(schema="...")` en vez de múltiples DataSources

**Alternativa considerada**: Dos DataSources independientes (uno por módulo) con sus propios EntityManagerFactory y TransactionManager.

**Por qué se descartó**: Spring Boot 4.0.6 ignora el atributo `transactionManager` de `@Transactional` cuando hay múltiples `PlatformTransactionManager`. El `@Primary` siempre se usa, causando que las operaciones de un módulo ejecuten en el transaction manager equivocado y los INSERTs nunca commiteen.

**Decisión**: Un solo DataSource HikariCP compartido, un solo EntityManagerFactory que escanea ambos paquetes de entidades, un solo TransactionManager. La separación de schemas se logra exclusivamente con `@Table(schema = "reservation")` / `@Table(schema = "payment")` en cada entidad JPA.

### 2. Dos instancias Flyway manuales

Spring Boot auto-configura un solo Flyway. Para manejar dos schemas con ubicaciones de migración distintas, se crean dos beans Flyway manuales (`reservationFlyway`, `paymentFlyway`) con `initMethod = "migrate"`. Se desactiva la auto-configuración con `spring.flyway.enabled: false`.

Cada instancia tiene su propia tabla `flyway_schema_history` dentro de su schema, evitando conflictos de lock.

### 3. HikariCP sin `connectionInitSql` de schema

No se configura `search_path` a nivel de pool. El `JdbcTemplate` (auto-configurado) usa el path por defecto (`public`). Las queries en tests de integración usan nombres cualificados (`reservation.zone_stock`, `payment.payments`). JPA cualifica las tablas automáticamente via `@Table(schema="...")`.

### 4. Schemas creados en init de PostgreSQL + Flyway

- `docker/postgres/init/01-create-schemas.sql`: Crea los schemas al inicializar el contenedor por primera vez
- Flyway `createSchemas(true)`: Crea schemas si no existen (idempotente, cubre el caso de schemas ya creados)

## Risks / Trade-offs

- **[Breaking change]** Las tablas existentes en `public` no se migran automáticamente → requiere `docker compose down -v` para recrear desde cero. En entornos productivos con datos, se necesitaría un script de migración adicional.
- **[Naming strategy]** Al crear el EntityManagerFactory manualmente, `hibernate.physical_naming_strategy` debe configurarse explícitamente a `CamelCaseToUnderscoresNamingStrategy`. Sin esto, Hibernate 6 busca columnas en camelCase y falla.
- **[JdbcTemplate en tests]** El `JdbcTemplate` auto-configurado usa el path `public`. Todas las queries SQL en tests deben usar nombres cualificados de schema.
