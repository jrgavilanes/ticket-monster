# Ticket Monster — Guía de Instalación y Pruebas

Guía para levantar **Ticket Monster** en local, desplegarlo en un clúster K3s remoto y probar el sistema con el emulador CLI `frontend.sh`.

---

## 1. Prerrequisitos

### 1.1 Software común

| Herramienta | Versión | Uso |
|-------------|---------|-----|
| Docker + Docker Compose | 24+ | Infraestructura (PostgreSQL, MongoDB, Redis, Redpanda, Keycloak) y app |
| JDK | 21 | Compilar y ejecutar el monolito (solo en modo nativo) |
| Gradle | 9.5+ | Build del backend (lo trae el wrapper `./gradlew`) |
| `curl` | — | Llamadas HTTP al API |
| `jq` | — | Formateo de respuestas JSON (recomendado) |
| `kubectl` | — | Despliegue remoto en K3s |

### 1.2 Software para el cluster remoto (K3s)

| Herramienta | Uso |
|-------------|-----|
| `k3s` | Orquestador (instalado por el script de provisionamiento) |
| `helm` | Despliegue de charts |
| `cert-manager` | Certificados TLS (Let's Encrypt) |
| Dominio propio | Acceso HTTPS público |

### 1.3 Usuarios de prueba (incluidos en Keycloak)

| Usuario | Password | Roles Keycloak | Menú frontend |
|---------|----------|----------------|---------------|
| `admin` | `admin` | ADMIN, USER | Crear artista/venue/evento, publicar, listar, disponibilidad |
| `user` | `user` | USER | Listar eventos, disponibilidad, comprar, pagar |

---

## 2. Instalación local (Docker Compose)

Es la forma más rápida de probar el sistema. Levanta la app + toda la infraestructura + observabilidad en contenedores.

### 2.1 Levantar todo (perfil `app`)

Desde la raíz del repositorio:

```bash
docker compose --profile app up -d
```

Esto construye la imagen del monolito y arranca:

- **App**: `ticketmonster` en `http://localhost:8082`
- **Persistencia**: PostgreSQL (`:5432`), MongoDB (`:27017`), Redis (`:6379`)
- **Mensajería**: Redpanda (Kafka en `:19092`, Console en `:8081`)
- **Auth**: Keycloak en `:8180`
- **Observabilidad**: Prometheus, Loki, Tempo, Grafana

> **Nota:** el perfil `app` no inicia la suite de observabilidad por defecto. Si no la ves en `docker compose ps`, arráncala explícitamente:
>
> ```bash
> docker compose --profile app up -d prometheus loki tempo grafana
> ```

### 2.2 Levantar solo infraestructura (app nativa o desde IDE)

Si prefieres ejecutar el monolito en local sin contenedor (rebuilds más rápidos):

```bash
docker compose --profile infra up -d
```

La app quedará esperando en:

- PostgreSQL → `localhost:5432`
- MongoDB → `localhost:27017`
- Redis → `localhost:6379`
- Redpanda → `localhost:19092` (no el `:9092` por defecto)
- Keycloak → `localhost:8180` (puerto desviado para evitar conflictos)

Después, ejecuta la app:

```bash
cd backend/ticketmonster
./gradlew bootRun
```

O desde IntelliJ, ejecuta la clase `TicketmonsterApplication` con el perfil por defecto.

### 2.3 Reconstruir la imagen de la app tras cambios de código

```bash
docker compose --profile app up -d --build ticketmonster
docker compose --profile app up -d prometheus loki tempo grafana   # si no estaban arriba
```

### 2.4 Reset completo (borra volúmenes)

```bash
docker compose down -v
```

---

## 3. Instalación remota (K3s en VPS)

Despliega el sistema en un clúster K3s con TLS automático (Let's Encrypt), Traefik, observabilidad y HPA.

### 3.1 Estructura del despliegue

El repositorio incluye scripts de aprovisionamiento en `deploy/k3s/`:

```
deploy/k3s/
├── k3s-provision.sh         # Crea el clúster K3s y el namespace base
├── k3s-infrastructure.sh    # Despliega BDs, Keycloak, Grafana stack
├── k3s-publish-app.sh       # Despliega el monolito
├── k3s-recreate.sh          # Wipe+redeploy conservando certificados TLS
└── deploy.sh                # Atajo: provision + infra + app en un solo paso
```

### 3.2 Despliegue paso a paso

```bash
# 1. Provisionar el clúster (incluye Traefik, cert-manager, namespaces)
./deploy/k3s/k3s-provision.sh -u janrax@janrax.es -d janrax.es

# 2. Desplegar infraestructura (PostgreSQL, MongoDB, Redis, Redpanda, Keycloak, Prometheus, Grafana, Loki, Tempo)
./deploy/k3s/k3s-infrastructure.sh -u janrax@janrax.es -d janrax.es

# 3. Publicar el monolito
./deploy/k3s/k3s-publish-app.sh -u janrax@janrax.es -d janrax.es
```

O todo en uno:

```bash
./deploy/k3s/deploy.sh janrax@janrax.es janrax.es latest
```

> **Staging vs producción:** durante iteraciones frecuentes usa `-s` (staging de Let's Encrypt) para evitar agotar la cuota de certificados (5 por dominio cada 7 días). Los certs de staging no son confiables para navegadores, pero el OAuth interno entre Grafana y Keycloak sigue funcionando porque va por HTTP dentro del cluster.

### 3.3 Recrear infraestructura conservando TLS

Si necesitas wipe+redeploy (cambios en realm de Keycloak, valores de Helm, etc.) pero quieres conservar los certificados TLS emitidos para no chocar con la rate limit de Let's Encrypt:

```bash
./deploy/k3s/k3s-recreate.sh -u janrax@janrax.es -d janrax.es
```

Fases que ejecuta:

1. **Backup** — guarda los secretos TLS (`<domain>-tls`) de los namespaces `infrastructure` y `observability`
2. **Destroy** — borra los namespaces `infrastructure`, `observability`, `ticket-monster`
3. **Re-provision** — re-ejecuta `k3s-infrastructure.sh` desde cero
4. **Restore** — restaura los secretos TLS y limpia los `Certificate` resources para que cert-manager no re-emita

### 3.4 Cambiar de staging a producción (sin destruir el cluster)

Si aprovisionaste con `-s` y quieres pasar a certs de producción:

```bash
kubectl delete clusterissuer letsencrypt

cat <<'YAML' | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@janrax.es
    privateKeySecretRef:
      name: letsencrypt-account-key
    solvers:
    - http01:
        ingress:
          ingressClassName: traefik
YAML

kubectl delete certificate --all -n infrastructure
kubectl delete certificate --all -n observability
kubectl get certificates -A -w   # esperar Ready=True
```

Datos y servicios (PostgreSQL, MongoDB, Redis) no se tocan. Solo se re-emiten los certificados.

### 3.5 Imagen del monolito

Las imágenes se construyen y publican manualmente vía GitHub Actions:

```
.github/workflows/docker-publish.yml
  → ghcr.io/jrgavilanes/ticket-monster:<version>
```

`k3s-publish-app.sh -t <version>` permite fijar la versión a desplegar.

---

## 4. Endpoints tras la instalación

| Servicio | Local (Docker Compose) | Remoto (K3s + Traefik) |
|----------|------------------------|------------------------|
| Monolith / API | `http://localhost:8082` | `https://<domain>` |
| GraphQL | `http://localhost:8082/graphql` | `https://<domain>/graphql` |
| Keycloak | `http://localhost:8180` | `https://<domain>/auth` |
| Keycloak Admin | `http://localhost:8180/admin` | `https://<domain>/auth/admin` |
| Redpanda Console | `http://localhost:8081` | (interno) |
| Grafana | `http://localhost:3000` | `https://<domain>/panel` |
| Prometheus | `http://localhost:9090` | (interno) |

---

## 5. Probar el sistema con `frontend.sh`

`frontend/frontend.sh` es un emulador CLI interactivo que recorre los flujos de administración y compra sin tener que escribir `curl` a mano. Detecta el rol del usuario (ADMIN/USER) mostrando el menú correspondiente.

### 5.1 Prerrequisitos

```bash
sudo apt install jq   # recomendado (mejora el formato JSON)
```

### 5.2 Uso básico

```bash
./frontend/frontend.sh <usuario> <password> [monolith_url] [keycloak_url] [-v]
```

| Argumento | Default | Descripción |
|-----------|---------|-------------|
| `usuario` | (requerido) | Usuario de Keycloak (`admin` o `user`) |
| `password` | (requerido) | Password del usuario |
| `monolith_url` | `https://janrax.es` | URL del monolito |
| `keycloak_url` | `https://janrax.es/auth` | URL de Keycloak |
| `-v` | off | Modo verbose: muestra el `curl` equivalente antes de cada llamada |

Al arrancar hace health-check de los dos endpoints y luego presenta el menú.

### 5.3 Sesión administrador (crear evento de prueba)

```bash
./frontend/frontend.sh admin admin http://localhost:8082 http://localhost:8180
```

Recorrido sugerido:

1. **Crear Artista** → `Foo Fighters`, `Rock`
2. **Crear Venue** → `Wembley`, capacidad `90000`
3. **Crear Evento** → nombre `Foo Fighters Live`, tipo `CONCERT`, fecha del futuro, `venueId` del paso 2
   - Zona 1: `Pista`, capacidad `40000`, precio `80.0`
   - Zona 2: `Grada`, capacidad `30000`, precio `120.0`
4. **Publicar Evento** → `eventId` del paso 3
5. **Listar Eventos** para verificar

> El script imprime los IDs que necesitas en pasos posteriores (artistId, venueId, eventId, zoneId).

### 5.4 Sesión usuario (compra end-to-end)

```bash
./frontend/frontend.sh user user http://localhost:8082 http://localhost:8180
```

Recorrido sugerido (ejecutar en otro terminal como `admin` y copiar el `eventId`):

1. **Listar Eventos** → anotar `eventId`
2. **Ver disponibilidad** → confirmar stock
3. **Comprar entradas** → el script:
   - Llama `POST /api/v1/queue/{eventId}/join` (Redis FIFO)
   - Hace polling a `GET /api/v1/queue/{eventId}/status` hasta `TURN_READY`
   - Pide `zoneId` y `quantity`, y crea la reserva
   - Devuelve `reservationId`
4. **Pagar reserva** → el script:
   - Recupera la reserva y valida que está `ACTIVE`
   - Calcula el monto desde los precios del catálogo
   - Crea el pago (`POST /api/v1/payments`) y lo confirma (`POST /api/v1/payments/{id}/confirm`)
5. **Ver reserva** → debería estar en estado `SOLD`

### 5.5 Modo verbose

Añade `-v` para que cada llamada muestre el `curl` equivalente antes de ejecutarla. Útil para depurar o copiar/pegar en un script propio:

```bash
./frontend/frontend.sh admin admin http://localhost:8082 http://localhost:8180 -v
```

### 5.6 Menú completo

**ADMIN:**

```
1. Crear Artista
2. Crear Venue
3. Crear Evento
4. Publicar Evento
5. Listar Eventos
6. Ver disponibilidad
7. Salir
```

**USER:**

```
1. Listar Eventos
2. Ver disponibilidad
3. Comprar entradas
4. Pagar reserva
5. Ver reserva
6. Salir
```

---

## 6. Verificación rápida (curl directo)

Si prefieres saltarte el CLI y validar el API a mano:

```bash
# 1. Obtener token
TOKEN=$(curl -s -X POST http://localhost:8180/realms/ticket-monster/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ticket-monster-app" \
  -d "username=user" -d "password=user" \
  -d "grant_type=password" | jq -r '.access_token')

# 2. Listar eventos (GraphQL público)
curl -s http://localhost:8082/graphql \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"query": "{ events(page: 0, size: 10) { content { id name } } }"}'

# 3. Unirse a la cola
curl -s -X POST http://localhost:8082/api/v1/queue/EVENT_ID/join \
  -H "Authorization: Bearer $TOKEN"
```

---

## 7. Consolas de inspección (local)

```bash
# PostgreSQL — reservas, pagos
docker exec -it ticket-monster-postgres-1 psql -U ticketmonster -d ticketmonster

# MongoDB — catálogo
docker exec -it ticket-monster-mongodb-1 mongosh admin -u ticketmonster -p ticketmonster

# Redis — colas, locks
docker exec -it ticket-monster-redis-1 redis-cli

# Redpanda — tópicos Kafka
docker exec -it ticket-monster-redpanda-1 rpk topic list
docker exec -it ticket-monster-redpanda-1 rpk topic consume payment-confirmed -n 5

# Keycloak Admin
# http://localhost:8180/admin (admin / admin)

# Grafana (con OAuth Keycloak)
# http://localhost:3000 → "Sign in with Keycloak" → admin / admin
```

---

## 8. Tests

Hay dos niveles de pruebas: **unit tests** (Gradle) y **load tests** (k6 contra infraestructura levantada).

### 8.1 Unit tests (Gradle)

Ejecutan la suite de tests del backend. El build falla si algún test no pasa (es el guardrail que usan `build-and-push.sh` y CI).

```bash
cd backend/ticketmonster
./gradlew test --no-daemon
```

Para correr un test específico:

```bash
./gradlew test --tests "com.example.catalog.*"
./gradlew test --tests "*ReservationServiceTest"
```

Los reportes JUnit quedan en `backend/ticketmonster/build/reports/tests/test/index.html`.

### 8.2 Load tests (k6)

Validan el comportamiento bajo concurrencia extrema. Los scripts `run-*.sh` crean los datos necesarios (venues, artistas, eventos, usuarios de Keycloak) y luego ejecutan el test.

**Prerrequisitos comunes:**

```bash
# Instalar k6 (Debian 13 / Trixie)
sudo apt-get update && sudo apt-get install -y gnupg
curl -fsSL https://dl.k6.io/key.gpg | sudo gpg --dearmor -o /usr/share/keyrings/k6.gpg
echo "deb [signed-by=/usr/share/keyrings/k6.gpg] https://dl.k6.io/deb stable main" \
  | sudo tee /etc/apt/sources.list.d/k6.list
sudo apt-get update && sudo apt-get install -y k6
```

Infraestructura + app + observabilidad levantadas:

```bash
docker compose --profile app up -d prometheus loki tempo grafana
```

#### Tests disponibles

| Script | Endpoint que golpea | Objetivo |
|--------|---------------------|----------|
| `deploy/tests/k6/run-catalog-read.sh` | `POST /graphql` | Throughput de lecturas GraphQL + MongoDB |
| `deploy/tests/k6/run-queue-load.sh` | `POST /api/v1/queue/{eventId}/join` | Throughput Redis bajo avalancha |
| `deploy/tests/k6/run-reservation-contention.sh` | `POST /api/v1/reservations` | Cero overbooking bajo concurrencia extrema |

Cada script:

1. Crea venue, artista, evento (con zonas) como admin
2. Crea los usuarios `k6user1`..`k6userN` en Keycloak
3. Limpia locks Redis y reservas PostgreSQL de runs anteriores
4. Ejecuta el test k6 con los VUs/duración definidos

#### Ejecución local

```bash
./deploy/tests/k6/run-catalog-read.sh
./deploy/tests/k6/run-queue-load.sh
./deploy/tests/k6/run-reservation-contention.sh
```

> Por defecto `BASE_URL=http://localhost:8082`. Para cargas altas (5K+ VUs) reduce los valores en local:
>
> ```bash
> k6 run --vus 100 --duration 10s deploy/tests/k6/catalog-read.js
> ```

Los resultados se envían a Prometheus con el output `experimental-prometheus-rw` y se visualizan en Grafana → dashboard **k6 Prometheus** (`http://localhost:3000`).

#### Ejecución contra K3s (producción)

**Limitación importante:** Prometheus en K3s es un servicio `ClusterIP` (`prometheus.observability.svc.cluster.local:9090`), no expuesto externamente. El endpoint `/api/v1/write` solo es alcanzable desde dentro del cluster. Por eso hace falta un `kubectl port-forward`.

**Opción A — desde local contra `janrax.es`:**

```bash
# Terminal 1 (VPS) — túnel a Prometheus
kubectl port-forward -n observability prometheus-0 9090:9090

# Terminal 2 (local) — k6 contra el túnel
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),min,max \
k6 run -o experimental-prometheus-rw --tag testid=catalog-read \
  --env BASE_URL=https://janrax.es deploy/tests/k6/catalog-read.js
```

**Opción B — k6 dentro del VPS (recomendado para 5K+ VUs):**

```bash
# Terminal 1 (VPS) — túnel en background
kubectl port-forward -n observability svc/prometheus 9090:9090 &

# Terminal 2 (VPS) — k6
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),min,max \
k6 run -o experimental-prometheus-rw --tag testid=catalog-read \
  --env BASE_URL=https://janrax.es deploy/tests/k6/catalog-read.js

# Stress test (5000 VUs, 30s)
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),min,max \
k6 run -o experimental-prometheus-rw --tag testid=catalog-read \
  --env BASE_URL=https://janrax.es deploy/tests/k6/catalog-read.js \
  --vus 5000 --duration 30s
```

**Tests autenticados contra producción:**

```bash
TOKEN=$(curl -s -X POST https://janrax.es/auth/realms/ticket-monster/protocol/openid-connect/token \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ticket-monster-app" \
  -d "username=admin" -d "password=admin" \
  -d "grant_type=password" | jq -r '.access_token')

EVENT_ID=$(curl -s https://janrax.es/graphql \
  -H "Content-Type: application/json" \
  -d '{"query":"{ events(page: 0, size: 1) { content { id } } }"}' | jq -r '.data.events.content[0].id')

K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
K6_PROMETHEUS_RW_TREND_STATS=p(95),p(99),min,max \
k6 run -o experimental-prometheus-rw --tag testid=queue-load \
  --env BASE_URL=https://janrax.es --env AUTH_TOKEN="$TOKEN" --env EVENT_ID="$EVENT_ID" \
  deploy/tests/k6/queue-load.js
```

#### Qué mide cada test

- **catalog-read.js** — p95 < 200ms objetivo. Mide el thread pool HTTP + capacidad de MongoDB.
- **queue-load.js** — mide Redis `LPUSH` puro. Es AP, no toca PostgreSQL.
- **reservation-contention.js** — valida **cero overbooking**: 1000 VUs compiten por 10 tickets en una zona. Solo 10 deben recibir `201 Created`, los otros 990 un `409 Conflict` legítimo. p95 alto (~3 s) es esperado por el `SELECT FOR UPDATE` que serializa las transacciones.

---

## 9. Solución de problemas

| Síntoma | Causa probable | Solución |
|---------|----------------|----------|
| `Monolith no responde en http://localhost:8082` | App no arrancó o perfil equivocado | `docker compose ps` y revisar logs con `docker compose logs ticketmonster` |
| `Prometheus / Loki / Tempo no aparecen` | Se arrancó solo el stack de la app | `docker compose --profile app up -d prometheus loki tempo grafana` |
| Login Keycloak falla con `401` | Realm aún no inicializado | Esperar ~30 s tras el primer arranque; Keycloak importa `realm-export.json` al inicio |
| `404 Evento no encontrado` en `POST /join` | `eventId` incorrecto o evento no publicado | Listar eventos con el CLI o GraphQL y verificar `status: PUBLISHED` |
| Cola virtual en `WAITING` para siempre | El dispatcher (`@Scheduled`) necesita app arriba en al menos 1 réplica | Verificar health del pod/replica de `ticketmonster` |
| `curl: (7) Failed to connect to localhost:19092` | Redpanda no expone Kafka en el puerto por defecto | Confirmar puerto en `docker-compose.yml`; el cliente debe usar `19092` |
| Let's Encrypt agota cuota de certificados | Demasiados deploys contra el mismo dominio | Usar `-s` (staging) o `k3s-recreate.sh` para preservar TLS |
