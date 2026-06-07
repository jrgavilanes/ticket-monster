## 1. Infraestructura Docker

- [x] 1.1 Crear script `docker/postgres/init/01-create-schemas.sql` con `CREATE SCHEMA IF NOT EXISTS` para reservation, payment, keycloak
- [ ] 1.2 Montar volumen `./docker/postgres/init:/docker-entrypoint-initdb.d` en el servicio postgres de `docker-compose.yml`
- [ ] 1.3 Añadir `KC_DB_SCHEMA: keycloak` al servicio keycloak en `docker-compose.yml`

## 2. Configuracion Spring Boot

- [ ] 2.1 Cambiar `spring.jpa.hibernate.ddl-auto` a `none` en `application.yml`
- [ ] 2.2 Cambiar `spring.flyway.enabled` a `false` en `application.yml`
- [ ] 2.3 Añadir propiedades `app.datasource.reservation-schema` y `app.datasource.payment-schema`

## 3. Entidades JPA

- [ ] 3.1 Añadir `@Table(schema = "reservation")` a `Reservation`, `ReservationItem`, `ZoneStock`
- [ ] 3.2 Añadir `@Table(schema = "payment")` a `Payment`, `PaymentAudit`

## 4. DataSource y Flyway

- [ ] 4.1 Crear `DataSourceConfig.java` con DataSource HikariCP unificado + EntityManagerFactory + TransactionManager
- [ ] 4.2 Configurar `hibernate.physical_naming_strategy` a `CamelCaseToUnderscoresNamingStrategy`
- [ ] 4.3 Añadir beans Flyway `reservationFlyway` y `paymentFlyway` con sus ubicaciones y schemas
- [ ] 4.4 Eliminar `ReservationDataSourceConfig.java` y `PaymentDataSourceConfig.java`

## 5. Migraciones Flyway

- [ ] 5.1 Mover `V1__create_reservation_schema.sql` a `db/migration/reservation/`
- [ ] 5.2 Mover `V2__create_payment_schema.sql` a `db/migration/payment/`

## 6. Configuracion de modulos

- [ ] 6.1 Simplificar `ReservationConfig.java`: quitar `entityManagerFactoryRef` y `transactionManagerRef`
- [ ] 6.2 Simplificar `PaymentConfig.java`: quitar `entityManagerFactoryRef` y `transactionManagerRef`

## 7. Tests

- [ ] 7.1 Actualizar `FullFlowIntegrationTest.java`: SQL con nombres cualificados (`reservation.zone_stock`, `payment.payments`, etc.)

## 8. Despliegue K3s

- [ ] 8.1 Añadir `RESERVATION_SCHEMA` y `PAYMENT_SCHEMA` en `deploy/charts/ticketmonster/values.yaml`
- [ ] 8.2 Cambiar Keycloak en `scripts/provision-infra.sh`: `externalDatabase.database=ticketmonster` + `externalDatabase.schema=keycloak`

## 9. Verificacion

- [ ] 9.1 Ejecutar `./gradlew test` — todos los tests pasan (62 tests, 0 fallos)
- [ ] 9.2 Ejecutar `docker compose --profile dev down -v && docker compose --profile dev up -d` — schemas creados, migraciones ejecutadas, app arranca
