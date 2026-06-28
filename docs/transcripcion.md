# Transcripción — Ticket Monster

Guion sugerido para el vídeo. Lenguaje cercano, basado en el SAD (`README.md`) y en la guía de instalación (`INSTALLATION_GUIDE.md`). Cada bloque indica qué se ve en pantalla y qué decir aproximadamente.

> Ritmo目安: vídeo de ~12–15 min. Las slides de diagrama son las que más pesan (~45 s cada una); las de texto van más rápido.

---

## Slide 1 — Carátula

**En pantalla:** "Ticket Monster" en grande, "Sistema de Reservas · Alta Concurrencia", tu nombre, fecha, bootcamp.

> "Hola, soy Juan Ramón Gavilanes y esto es **Ticket Monster**, el proyecto de fin de bootcamp de Arquitecturas Backend a Gran Escala con Java.
>
> La idea era construir un sistema de venta de tickets como el que usarías para comprar entradas de un concierto de Foo Fighters en Wembley, pero diseñado para aguantar los **cinco millones de usuarios concurrentes** que se conectan a la vez cuando se abre la venta de un evento grande.
>
> Y con un requisito no negociable: **cero overbooking**. Nunca, bajo ninguna condición de concurrencia, se puede vender más entradas de las que caben en el recinto."

---

## Slide 2 — El repositorio

**En pantalla:** URL del repo + 3 cards: README.md, INSTALLATION_GUIDE.md, frontend.sh.

> "Todo el proyecto está en abierto en `github.com/jrgavilanes/ticket-monster`.
>
> Dentro vas a encontrar tres cosas que te interesa saber:
>
> Primero, el **README.md** es un SAD completo — un documento de arquitectura de software de unas 2.000 líneas. Está organizado en 17 secciones más tres anexos. Es lo que vamos a recorrer hoy.
>
> Segundo, la **INSTALLATION_GUIDE.md** explica cómo levantarlo en local con Docker Compose, cómo desplegarlo en un cluster K3s en un VPS, y cómo correr los tests de carga con k6. Todo con un solo comando cada uno.
>
> Y tercero, `frontend/frontend.sh`: un emulador CLI interactivo que te permite hacer todo el recorrido de la aplicación — crear eventos como admin, comprar entradas como user — sin tener que escribir una sola línea de `curl`. Lo veremos al final."

---

## Slide 3 — Flujo de compra end-to-end

**En pantalla:** Diagrama de secuencia con los 6 pasos: cola → polling → token → reserva → pago → confirmación.

> "Antes de meternos en arquitectura, vamos a ver el flujo completo de una compra. Porque todo el diseño del sistema sale de entender este flujo.
>
> Fíjate: son **seis pasos explícitos**, no una sola transacción atómica. ¿Por qué? Porque cada paso puede escalar y fallar de forma independiente. El primero, unirse a la cola, es el que recibe los 5 millones de usuarios. El último, confirmar el pago, solo lo ven unos pocos miles.
>
> El usuario llega, hace `POST /join` y se encola en Redis. Le devolvemos un `ticketId` y su posición. Mientras espera, hace polling a `GET /status`. Cuando el dispatcher lo selecciona, le llega un token HMAC con cinco minutos de vida. Con ese token ya puede llamar a `POST /reservations`, que es donde está el corazón del sistema: el **doble lock** que garantiza cero overbooking. Si todo va bien, publicamos un evento `reservation-created` en Redpanda.
>
> Luego vienen el pago y su confirmación por idempotency key, y al final un evento `payment-confirmed` vuelve al módulo de reservas para convertirla en SOLD.
>
> Esta separación no es capricho. La fila virtual absorbe la avalancha antes de tocar PostgreSQL, y el token HMAC actúa como mecanismo de control de flujo entre la cola y el motor de reservas."

---

## Slide 4 — C4 Nivel 1: Contexto

**En pantalla:** Usuario → Traefik (Edge) → Sistema + Keycloak.

> "Empezamos con el diagrama de contexto, el primero de la metodología C4. Responde a la pregunta más básica: ¿qué es este sistema y con quién habla?
>
> El usuario, desde el navegador o el móvil, llega a **Traefik**, que es el ingress controller del cluster K3s. Traefik hace tres cosas importantes: termina el TLS con certificados de Let's Encrypt, aplica **rate limiting** a los endpoints de escritura y mete cabeceras de seguridad HTTP.
>
> En paralelo, **Keycloak** se encarga de la autenticación OAuth2/OIDC. Emite tokens JWT que el monolito valida internamente con Spring Security.
>
> Lo que quiero que te fijes es que el sistema es **autocontenido**: no depende de ninguna pasarela de pago externa — el módulo de pagos es una simulación con idempotencia — ni de ningún proveedor cloud específico. Todo se despliega en un cluster K3s sobre un VPS, que en mi caso es uno de 12 euros al mes."

---

## Slide 5 — C4 Nivel 2: Contenedores

**En pantalla:** Edge, monolito con 4 módulos, persistencia, eventos, observabilidad.

> "Bajamos un nivel. Aquí ya vemos los **contenedores**: las piezas que se despliegan y cómo se comunican.
>
> El monolito está construido con **Spring Modulith** y contiene **cuatro módulos**, cada uno un bounded context de DDD. Catalog expone una API GraphQL pública. Virtual Queue implementa la cola FIFO. Reservation es el núcleo transaccional con el doble lock. Y Payment gestiona el ciclo de vida del pago.
>
> En la capa de persistencia ves la **persistencia poliglota**: cada bounded context usa el motor que mejor le encaja. MongoDB para el catálogo por su esquema flexible. PostgreSQL para reservas y pagos por su ACID. Y Redis para colas y locks por su velocidad.
>
> Fíjate en una cosa importante: PostgreSQL usa **schemas separados** — uno para `reservation`, otro para `payment` — para que cuando extraigamos un módulo a microservicio, no haya que migrar datos.
>
> La comunicación asíncrona va por **Redpanda**, que es un broker compatible con Kafka pero más ligero — un solo binario, sin ZooKeeper.
>
> Y por último, todo el stack de observabilidad: Grafana, Prometheus, Loki y Tempo. Métricas vía Micrometer, logs JSON vía Logback y trazas distribuidas con el agente de OpenTelemetry."

---

## Slide 6 — C4 Nivel 3: Catalog Module

**En pantalla:** Componentes internos del Catalog Module: controllers, repos, interfaz AvailabilityService.

> "Si abrimos el módulo de catálogo, vemos sus tripas.
>
> El `EventQueryController` resuelve las queries de GraphQL: listar eventos, buscar por texto, filtrar por tipo y fecha. El `EventMutationController` gestiona las mutaciones — crear eventos, venues, artistas — y está protegido con `@PreAuthorize` para que solo un admin pueda escribir.
>
> Lo interesante es la query `availability`. Aunque vive en el módulo de catálogo, **no consulta MongoDB** — consulta PostgreSQL a través del módulo de Reservation. Y lo hace a través de una interfaz Java, `AvailabilityService`, que está definida aquí pero implementada en otro módulo. Esta es la **única dependencia síncrona** entre bounded contexts, y está así por diseño: la disponibilidad es solo lectura y necesita datos en tiempo real.
>
> Cuando el admin crea o modifica zonas, Catalog publica un `ZonesModifiedEvent` — un Spring ApplicationEvent interno. Reservation lo escucha y sincroniza su tabla `zone_stock` en PostgreSQL. Si mañana extraemos Catalog a microservicio, ese evento interno se convierte en un tópico de Redpanda, pero la lógica no cambia."

---

## Slide 7 — C4 Nivel 3: Virtual Queue Module

**En pantalla:** QueueController, QueueService, QueueDispatcher, QueueTokenService.

> "El módulo de la cola virtual es deliberadamente el más simple. Solo habla con Redis.
>
> El `QueueController` expone los tres endpoints REST: join, status y token. El `QueueService` implementa la lógica FIFO: `LPUSH` para encolar al usuario — operación O(1) en microsegundos — y `LRANGE` para buscar su posición exacta en la lista.
>
> La joya es el `QueueDispatcher`: un job con `@Scheduled` que cada dos segundos extrae un lote de 500 usuarios con `RPOP` y los marca como `TURN_READY` en un hash. Esto es **control de flujo puro**: convierte los 5 millones de usuarios concurrentes en **250 reservas por segundo** que llegan al motor. Sin esto, PostgreSQL moriría.
>
> Y el `QueueTokenService` emite un token HMAC-SHA256 cuando llega el turno. Cinco minutos de TTL. ¿Por qué HMAC y no JWT de Keycloak? Porque validar un HMAC local son microsegundos, mientras que ir a Keycloak son decenas de milisegundos. En el path caliente de compra, cada milisegundo cuenta."

---

## Slide 8 — C4 Nivel 3: Reservation Module

**En pantalla:** Componentes del Reservation Module: Service, Lock, Publisher, Sweeper, Listener.

> "Llegamos al módulo más complejo. Reservation es el guardián del cero overbooking.
>
> El `ReservationService` orquesta el flujo central: valida el límite de 3 tickets por usuario, ejecuta `SELECT FOR UPDATE` sobre `zone_stock`, adquiere el lock de Redis, decrementa el stock y persiste la reserva. También publica el evento `reservation-created` en Redpanda.
>
> El `DistributedLockService` implementa los locks con `SETNX` y TTL. La key es `reservation:{eventId}:{zoneId}:{userId}` — un lock por usuario y zona. El TTL de 10 minutos garantiza que si el servidor se cae, el lock no queda huérfano para siempre.
>
> Hay dos piezas defensivas que quiero destacar. La primera es el `ReservationExpirationSweeper`, un job que cada 60 segundos barre reservas expiradas. Es el **fallback** por si las keyspace notifications de Redis fallan — y fallan, son best-effort.
>
> La segunda es el `PaymentConfirmedListener`. Es un `@KafkaListener` que consume el tópico `payment-confirmed` de Redpanda. Cuando llega, llama a `confirmSale()` y convierte la reserva de `ACTIVE` a `SOLD`. Es el único camino por el que una reserva pasa a vendida.
>
> Fíjate también en la `ZoneStockEventProcessor`: escucha los `ZonesModifiedEvent` que manda Catalog y mantiene la tabla `zone_stock` sincronizada."

---

## Slide 9 — C4 Nivel 3: Payment Module

**En pantalla:** PaymentController, PaymentService, dos repositorios JPA, schema separado en PostgreSQL, Redpanda.

> "El módulo de pagos es el más pequeño pero no por eso menos importante.
>
> El `PaymentService` implementa **idempotencia en ambas operaciones de escritura**. En el `POST /` con el `reservationId`: si ya existe un pago para esa reserva, devuelve el existente en lugar de crear uno nuevo. Y en el `POST /confirm` con la `idempotencyKey`: si llega la misma confirmación dos veces, la segunda no hace nada.
>
> Esto importa porque en un sistema distribuido, los clientes siempre reintentan. Si el botón "Confirmar pago" se pulsa dos veces, o si un timeout de red hace que la petición llegue dos veces, el resultado debe ser el mismo: un solo cargo.
>
> La tabla `payment_audit` es append-only: cada cambio de estado deja un rastro. No se borra ni se modifica. Esto nos da trazabilidad completa: si hay una disputa, podemos reconstruir qué pasó y cuándo.
>
> Y cuando se confirma el pago, publicamos `payment-confirmed` en Redpanda. Reservation lo consume y convierte la reserva a SOLD. Esa es la única manera de cerrar el ciclo."

---

## Slide 10 — Context Map (DDD)

**En pantalla:** Diagrama de los 4 bounded contexts con sus relaciones síncronas y asíncronas.

> "Aquí tenemos el context map de DDD. Es la foto de cómo se relacionan los bounded contexts entre sí.
>
> Catalog le habla a Reservation de **dos maneras**: una síncrona, cuando necesita la disponibilidad — recuerda, esa `AvailabilityService` que vimos antes. Y otra asíncrona, cuando crea o modifica zonas, vía un evento interno que Spring publica y Reservation escucha.
>
> Virtual Queue **no habla directamente con Reservation**. La comunicación es a través del cliente: el usuario recibe el token HMAC y se lo pasa a Reservation en la siguiente petición. Esto se llama relación de **Partnership** en DDD: están acopladas por protocolo, no por código.
>
> Reservation y Payment tampoco se hablan en línea. Se comunican exclusivamente por Redpanda. Reservation publica `reservation-created` y Payment lo consume. Payment publica `payment-confirmed` y Reservation lo consume para confirmar la venta.
>
> ¿Por qué? Por **desacoplamiento temporal**. Si Payment está caído, los eventos se acumulan en Redpanda y se procesan cuando se recupera. La reserva ya se creó, el usuario ya tiene su `reservationId` con 10 minutos de TTL. No hay acoplamiento que rompa el sistema.
>
> Y esto, además, nos prepara para extraer módulos a microservicios en el futuro sin tocar el protocolo de comunicación."

---

## Slide 11 — Modelo de datos: MongoDB

**En pantalla:** Diagrama ER de MongoDB con Event, Zone (embebido), Venue, Artist.

> "Modelo de datos. Empezamos por MongoDB, que es donde vive el catálogo.
>
> Eligié MongoDB por tres razones. La primera, **schema flexible**: un concierto, una obra de teatro y un partido de fútbol tienen atributos distintos, y un documento embebido evita migraciones constantes. La segunda, **rendimiento de lectura**: el catálogo es read-heavy, y MongoDB escala horizontal con replica sets. Y la tercera, **full-text search nativo** sin necesidad de montar un Elasticsearch.
>
> Fíjate en el diagrama: `Zone` está embebida dentro de `Event`. ¿Por qué? Porque una zona no existe sin su evento. Cuando lees un evento, siempre necesitas sus zonas. Es un patrón de acceso conjunto. Embeberlas evita los JOINs de MongoDB, que son carísimos.
>
> En cambio, `Venue` y `Artist` son colecciones independientes referenciadas por `venueId` y `artistIds`. ¿Por qué? Porque un mismo Wembley o un mismo artista participan en muchos eventos. Si los embebiera, estaría duplicando datos a lo loco.
>
> El catálogo entero ocupa unos 2 MB. Es un sistema read-heavy donde el almacenamiento no es el problema, lo es la concurrencia de lectura."

---

## Slide 12 — Modelo de datos: PostgreSQL

**En pantalla:** Diagrama ER de PostgreSQL: zone_stock, reservations, reservation_items, payments, payment_audit.

> "PostgreSQL es donde vive la verdad transaccional. Aquí sí necesitamos ACID, sí necesitamos `SELECT FOR UPDATE` y sí necesitamos garantías fuertes.
>
> La pieza central es `zone_stock`. Es una tabla pequeña — una fila por cada combinación de evento y zona — pero es **lo más importante del sistema**. Toda la concurrencia se serializa sobre estas filas.
>
> `reservations` guarda el estado de cada reserva: `ACTIVE`, `EXPIRED`, `CANCELLED` o `SOLD`. El status solo transiciona hacia adelante: una reserva activa puede expirarse, cancelarse o venderse, pero nunca vuelve atrás.
>
> `reservation_items` son las líneas de cada reserva: qué zona y cuántas entradas. Una reserva puede tener varias líneas si compras entradas de varias zonas a la vez.
>
> `payments` y `payment_audit` son la parte financiera. Cada pago tiene un `idempotency_key` único — esa es la columna que garantiza que un cargo no se procese dos veces aunque el cliente pulse el botón cinco veces. Y `payment_audit` es append-only: cada cambio de estado deja un registro inmutable.
>
> PostgreSQL usa **dos schemas separados**, uno por bounded context. Esto significa que mañana, si queremos extraer Payment a un microservicio, movemos el schema `payment` a otra instancia y listo. Cero migración de datos."

---

## Slide 13 — Cuestionario de evaluación (Anexo A)

**En pantalla:** Tabla con las 5 cuestiones A.1 a A.5, cada una con su respuesta resumida.

> "Esta es la slide que responde al cuestionario de evaluación del bootcamp. Cinco preguntas, cinco respuestas.
>
> **A.1 — Anti-overbooking.** Tres capas: límite de tickets por usuario, `SELECT FOR UPDATE` en PostgreSQL que serializa todas las transacciones, y `SETNX` en Redis para impedir que el mismo usuario haga doble reserva. Validado con k6: mil usuarios compitiendo por diez tickets, exactamente diez reservas creadas, cero overbooking.
>
> **A.2 — Resiliencia.** Cuatro niveles coordinados. Traefik en el edge, Resilience4j en la app con circuit breakers, TTL en Redis para los locks, y Redpanda con acks=all e idempotencia. Más el sweeper de 60 segundos como red de seguridad.
>
> **A.3 — Concurrencia.** Cuatro dimensiones. PostgreSQL serializa con `FOR UPDATE`, Redis es single-thread por naturaleza, Java 21 Virtual Threads nos dan millones de threads concurrentes, y la idempotencia en pagos cierra la última puerta.
>
> **A.4 — Cinco millones de usuarios.** La clave es la fila virtual. Redis con `LPUSH` absorbe la avalancha — son microsegundos por operación. El dispatcher luego extrae lotes de 500 usuarios cada 2 segundos, limitando la entrada a 250 por segundo. Y Traefik tira de rate limiting antes de que la petición llegue al backend.
>
> **A.5 — Teorema CAP.** Estrategia híbrida. Reservation y Payment son CP — cero overbooking e integridad financiera no se negocian. Catalog y Virtual Queue son AP — read-heavy, toleran consistencia eventual. Redpanda es CP por el Raft consensus. Distintas partes del sistema tienen distintos requisitos de consistencia."

---

## Slide 14 — Cierre

**En pantalla:** "Gracias" en grande, URL del repo, 3 chips con los recursos.

> "Y eso es todo.
>
> El código, el SAD y la guía de instalación están en abierto en `github.com/jrgavilanes/ticket-monster`. Léelo, pruébalo, rómpelo. Y si encuentras algo, abre un issue — que para eso está.
>
> Si quieres probarlo en local sin complicarte, tienes el `frontend.sh` que te recorre los flujos de admin y de usuario desde la terminal. Y si quieres romperlo a lo grande, tienes los scripts de k6 en `deploy/tests/k6` que simulan desde 100 hasta 5.000 usuarios virtuales.
>
> Gracias por llegar hasta aquí. Nos vemos en el repositorio."

---

## Notas de producción

- **Ritmo:** las slides 3, 4, 5 y 13 son las más densas — dales 50–60 s cada una. Las de diagramas individuales (6–12) son 30–45 s.
- **Pausas:** después de cada afirmación numérica (cinco millones, 250 por segundo, cero overbooking). Pausa de 1–2 s para que aterrice.
- **Gestos:** en las slides 3, 4 y 5 señala la pantalla con el ratón o con el dedo. En las de diagrama, recorre el flujo con la mano.
- **Tono:** evita sonar como leyendo. Si un número no te sale natural, parafraséalo. Mejor "muchos miles de usuarios" que tropecientas cifras.
- **Errores comunes:** no digas "hilos virtuales de Java 21" sin explicar antes que es Project Loom. No digas "Redpanda es como Kafka" sin decir por qué — single binary, sin ZooKeeper.
- **Cierre:** la slide 14 debería quedarse en pantalla unos 5 segundos en silencio después de "nos vemos en el repositorio" para que el espectador anote la URL.
