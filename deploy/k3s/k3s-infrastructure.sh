#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:-}"
DOMAIN="${2:-}"

if [ -z "$TARGET" ] || [ -z "$DOMAIN" ]; then
    echo "Uso: $0 <usuario@host> <dominio>"
    echo "Ejemplo: $0 janrax@janrax.es janrax.es"
    exit 1
fi

USER=$(echo "$TARGET" | cut -d'@' -f1)
HOST=$(echo "$TARGET" | cut -d'@' -f2)

ssh -t "$USER@$HOST" "cat > /tmp/infra-$$.sh << 'INFRA_SCRIPT'
#!/bin/bash
set -euo pipefail
DOMAIN=\$1

step() { echo -e \"\\n\\033[1;34m▶ \$1\\033[0m\"; }
ok()   { echo -e \"\\033[1;32m  ✓ \$1\\033[0m\"; }
skip() { echo -e \"\\033[1;33m  ⊘ \$1 (ya existe)\\033[0m\"; }

trap 'echo -e \"\\n\\033[1;31m✗ Error en la línea \$LINENO\\033[0m\"' ERR

export KUBECONFIG=\$HOME/.kube/config

# ── helpers ──────────────────────────────────────────────

gen_secret() {
    local name=\"\$1\" ns=\"\$2\"; shift 2
    if kubectl get secret \"\$name\" -n \"\$ns\" &>/dev/null; then
        skip \"Secret \$name (\$ns)\"; return 0
    fi
    local args=()
    for p in \"\$@\"; do
        local k=\"\${p%%=*}\" v=\"\${p#*=}\"
        [[ -z \"\$v\" ]] && v=\$(openssl rand -hex 32)
        args+=(--from-literal=\"\$k=\$v\")
    done
    kubectl create secret generic \"\$name\" -n \"\$ns\" \"\${args[@]}\"
    ok \"Secret \$name (\$ns)\"
}

copy_secret() {
    local name=\"\$1\" from=\"\$2\" to=\"\$3\"
    if kubectl get secret \"\$name\" -n \"\$to\" &>/dev/null; then
        skip \"Secret \$name en \$to\"; return 0
    fi
    kubectl get secret \"\$name\" -n \"\$from\" -o json \
        | sed \"s/\\\"namespace\\\": \\\"\$from\\\"/\\\"namespace\\\": \\\"\$to\\\"/\" \
        | kubectl apply -f -
    ok \"Secret \$name: \$from → \$to\"
}

get_secret() { kubectl get secret \"\$1\" -n \"\$2\" -o jsonpath=\"{.data.\$3}\" | base64 -d; }

apply_ns() {
    kubectl get namespace \"\$1\" &>/dev/null && { skip \"Namespace \$1\"; return 0; } || true
    kubectl create namespace \"\$1\"
    ok \"Namespace \$1\"
}

# apply_k8s kind name namespace << YAML
apply_k8s() {
    local kind=\"\$1\" name=\"\$2\" ns=\"\$3\"
    if kubectl get \"\$kind\" \"\$name\" -n \"\$ns\" &>/dev/null; then
        skip \"\$kind \$name\"; cat > /dev/null; return 0
    fi
    kubectl apply -f -
    ok \"\$kind \$name\"
}

# cfgmap name namespace key file [--force] << CONTENT
cfgmap() {
    local name=\"\$1\" ns=\"\$2\" key=\"\$3\" file=\"\$4\"
    if kubectl get configmap \"\$name\" -n \"\$ns\" &>/dev/null; then
        skip \"ConfigMap \$name\"; return 0
    fi
    cat > \"\$file\"
    kubectl create configmap \"\$name\" -n \"\$ns\" --from-file=\"\$key=\$file\"
    ok \"ConfigMap \$name\"
}

# ══════════════════════════════════════════════════════════
# NAMESPACES
# ══════════════════════════════════════════════════════════
step \"Creando namespaces...\"
apply_ns infrastructure
apply_ns ticket-monster
apply_ns observability

# ══════════════════════════════════════════════════════════
# SECRETS
# ══════════════════════════════════════════════════════════
step \"Generando Secrets...\"
gen_secret postgresql-credentials infrastructure postgres-password= ticketmonster-password=
gen_secret mongodb-credentials infrastructure mongodb-root-password= mongodb-password=
gen_secret redis-credentials infrastructure redis-password=
gen_secret keycloak-credentials infrastructure admin-password=
gen_secret grafana-credentials observability admin-password=

step \"Copiando Secrets a ticket-monster...\"
copy_secret postgresql-credentials infrastructure ticket-monster
copy_secret mongodb-credentials infrastructure ticket-monster
copy_secret redis-credentials infrastructure ticket-monster

# ══════════════════════════════════════════════════════════
# POSTGRESQL
# ══════════════════════════════════════════════════════════
step \"Deploying PostgreSQL...\"

apply_k8s statefulset postgresql infrastructure << 'ST'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgresql
  namespace: infrastructure
  labels: {app: postgresql}
spec:
  replicas: 1
  selector: {matchLabels: {app: postgresql}}
  serviceName: postgresql
  template:
    metadata: {labels: {app: postgresql}}
    spec:
      containers:
      - name: postgresql
        image: postgres:16-alpine
        env:
        - name: POSTGRES_USER
          value: ticketmonster
        - name: POSTGRES_DB
          value: ticketmonster
        - name: POSTGRES_PASSWORD
          valueFrom: {secretKeyRef: {name: postgresql-credentials, key: ticketmonster-password}}
        - name: PGDATA
          value: /var/lib/postgresql/data/pgdata
        ports: [{containerPort: 5432}]
        readinessProbe:
          exec: {command: [\"pg_isready\", \"-U\", \"ticketmonster\"]}
          initialDelaySeconds: 10
          periodSeconds: 5
        volumeMounts:
        - {name: data, mountPath: /var/lib/postgresql/data}
  volumeClaimTemplates:
  - metadata: {name: data}
    spec:
      accessModes: [\"ReadWriteOnce\"]
      resources: {requests: {storage: 10Gi}}
ST

apply_k8s service postgresql infrastructure << 'ST'
apiVersion: v1
kind: Service
metadata: {name: postgresql, namespace: infrastructure}
spec:
  ports: [{port: 5432, targetPort: 5432}]
  selector: {app: postgresql}
ST

# ══════════════════════════════════════════════════════════
# MONGODB
# ══════════════════════════════════════════════════════════
step \"Deploying MongoDB...\"

apply_k8s statefulset mongodb infrastructure << 'ST'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: mongodb
  namespace: infrastructure
  labels: {app: mongodb}
spec:
  replicas: 1
  selector: {matchLabels: {app: mongodb}}
  serviceName: mongodb
  template:
    metadata: {labels: {app: mongodb}}
    spec:
      containers:
      - name: mongodb
        image: mongo:7
        env:
        - name: MONGO_INITDB_ROOT_USERNAME
          value: ticketmonster
        - name: MONGO_INITDB_ROOT_PASSWORD
          valueFrom: {secretKeyRef: {name: mongodb-credentials, key: mongodb-root-password}}
        - name: MONGO_INITDB_DATABASE
          value: ticketmonster_catalog
        ports: [{containerPort: 27017}]
        readinessProbe:
          exec: {command: [\"mongosh\", \"--quiet\", \"--eval\", \"db.adminCommand('ping')\"]}
          initialDelaySeconds: 10
          periodSeconds: 5
        volumeMounts:
        - {name: data, mountPath: /data/db}
  volumeClaimTemplates:
  - metadata: {name: data}
    spec:
      accessModes: [\"ReadWriteOnce\"]
      resources: {requests: {storage: 10Gi}}
ST

apply_k8s service mongodb infrastructure << 'ST'
apiVersion: v1
kind: Service
metadata: {name: mongodb, namespace: infrastructure}
spec:
  ports: [{port: 27017, targetPort: 27017}]
  selector: {app: mongodb}
ST

# ══════════════════════════════════════════════════════════
# REDIS
# ══════════════════════════════════════════════════════════
step \"Deploying Redis...\"

apply_k8s statefulset redis infrastructure << 'ST'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: infrastructure
  labels: {app: redis}
spec:
  replicas: 1
  selector: {matchLabels: {app: redis}}
  serviceName: redis
  template:
    metadata: {labels: {app: redis}}
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        command: [\"redis-server\", \"--notify-keyspace-events\", \"Ex\"]
        ports: [{containerPort: 6379}]
        readinessProbe:
          exec: {command: [\"redis-cli\", \"ping\"]}
          initialDelaySeconds: 5
          periodSeconds: 5
        volumeMounts:
        - {name: data, mountPath: /data}
  volumeClaimTemplates:
  - metadata: {name: data}
    spec:
      accessModes: [\"ReadWriteOnce\"]
      resources: {requests: {storage: 5Gi}}
ST

apply_k8s service redis infrastructure << 'ST'
apiVersion: v1
kind: Service
metadata: {name: redis, namespace: infrastructure}
spec:
  ports: [{port: 6379, targetPort: 6379}]
  selector: {app: redis}
ST

# ══════════════════════════════════════════════════════════
# REDPANDA
# ══════════════════════════════════════════════════════════
step \"Deploying Redpanda...\"

apply_k8s statefulset redpanda infrastructure << 'ST'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redpanda
  namespace: infrastructure
  labels: {app: redpanda}
spec:
  replicas: 1
  selector: {matchLabels: {app: redpanda}}
  serviceName: redpanda
  template:
    metadata: {labels: {app: redpanda}}
    spec:
      containers:
      - name: redpanda
        image: redpandadata/redpanda:v24.3.5
        args:
        - redpanda
        - start
        - --smp 1
        - --memory 512M
        - --overprovisioned
        - --kafka-addr internal://0.0.0.0:9092
        - --advertise-kafka-addr internal://redpanda.infrastructure.svc.cluster.local:9092
        ports: [{containerPort: 9092, name: kafka}]
        readinessProbe:
          exec: {command: [\"rpk\", \"cluster\", \"health\"]}
          initialDelaySeconds: 20
          periodSeconds: 10
        volumeMounts:
        - {name: data, mountPath: /var/lib/redpanda/data}
  volumeClaimTemplates:
  - metadata: {name: data}
    spec:
      accessModes: [\"ReadWriteOnce\"]
      resources: {requests: {storage: 10Gi}}
ST

apply_k8s service redpanda infrastructure << 'ST'
apiVersion: v1
kind: Service
metadata: {name: redpanda, namespace: infrastructure}
spec:
  ports: [{port: 9092, targetPort: 9092, name: kafka}]
  selector: {app: redpanda}
ST

# ══════════════════════════════════════════════════════════
# KEYCLOAK
# ══════════════════════════════════════════════════════════
step \"Deploying Keycloak...\"

apply_k8s deployment keycloak infrastructure << 'ST'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: keycloak
  namespace: infrastructure
  labels: {app: keycloak}
spec:
  replicas: 1
  selector: {matchLabels: {app: keycloak}}
  template:
    metadata: {labels: {app: keycloak}}
    spec:
      containers:
      - name: keycloak
        image: quay.io/keycloak/keycloak:26.6.3
        args: [\"start\", \"--import-realm\"]
        env:
        - name: KC_DB
          value: postgres
        - name: KC_DB_URL
          value: jdbc:postgresql://postgresql.infrastructure.svc.cluster.local:5432/ticketmonster
        - name: KC_DB_USERNAME
          value: ticketmonster
        - name: KC_DB_PASSWORD
          valueFrom: {secretKeyRef: {name: postgresql-credentials, key: ticketmonster-password}}
        - name: KC_DB_SCHEMA
          value: keycloak
        - name: KEYCLOAK_ADMIN
          value: admin
        - name: KEYCLOAK_ADMIN_PASSWORD
          valueFrom: {secretKeyRef: {name: keycloak-credentials, key: admin-password}}
        - name: KC_PROXY_HEADERS
          value: xforwarded
        ports: [{containerPort: 8080, name: http}]
        readinessProbe:
          httpGet: {path: /health/ready, port: 8080}
          initialDelaySeconds: 30
          periodSeconds: 10
        livenessProbe:
          httpGet: {path: /health/live, port: 8080}
          initialDelaySeconds: 60
          periodSeconds: 15
ST

apply_k8s service keycloak infrastructure << 'ST'
apiVersion: v1
kind: Service
metadata: {name: keycloak, namespace: infrastructure}
spec:
  ports: [{port: 8080, targetPort: 8080}]
  selector: {app: keycloak}
ST

# ══════════════════════════════════════════════════════════
# PROMETHEUS
# ══════════════════════════════════════════════════════════
step \"Deploying Prometheus...\"

cfgmap prometheus-config observability prometheus.yml /tmp/prometheus.yml << 'CFG'
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'ticketmonster'
    metrics_path: '/actuator/prometheus'
    scrape_interval: 5s
    static_configs:
      - targets: ['ticketmonster.ticket-monster.svc.cluster.local:8082']
        labels:
          application: 'ticketmonster'
CFG

apply_k8s statefulset prometheus observability << 'ST'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: prometheus
  namespace: observability
  labels: {app: prometheus}
spec:
  replicas: 1
  selector: {matchLabels: {app: prometheus}}
  serviceName: prometheus
  template:
    metadata: {labels: {app: prometheus}}
    spec:
      containers:
      - name: prometheus
        image: prom/prometheus:v3.9.1
        args:
        - --config.file=/etc/prometheus/prometheus.yml
        - --storage.tsdb.path=/prometheus
        - --storage.tsdb.retention.time=7d
        ports: [{containerPort: 9090}]
        readinessProbe:
          httpGet: {path: /-/ready, port: 9090}
          initialDelaySeconds: 10
          periodSeconds: 5
        volumeMounts:
        - {name: config, mountPath: /etc/prometheus}
        - {name: data, mountPath: /prometheus}
      volumes:
      - name: config
        configMap: {name: prometheus-config}
  volumeClaimTemplates:
  - metadata: {name: data}
    spec:
      accessModes: [\"ReadWriteOnce\"]
      resources: {requests: {storage: 10Gi}}
ST

apply_k8s service prometheus observability << 'ST'
apiVersion: v1
kind: Service
metadata: {name: prometheus, namespace: observability}
spec:
  ports: [{port: 9090, targetPort: 9090}]
  selector: {app: prometheus}
ST

# ══════════════════════════════════════════════════════════
# LOKI
# ══════════════════════════════════════════════════════════
step \"Deploying Loki...\"

cfgmap loki-config observability local-config.yaml /tmp/loki-config.yaml << 'CFG'
auth_enabled: false

server:
  http_listen_port: 3100

common:
  path_prefix: /loki
  storage:
    filesystem:
      chunks_directory: /loki/chunks
      rules_directory: /loki/rules
  replication_factor: 1
  ring:
    kvstore:
      store: inmemory

schema_config:
  configs:
    - from: 2020-10-24
      store: tsdb
      object_store: filesystem
      schema: v13
      index:
        prefix: index_
        period: 24h

storage_config:
  tsdb_shipper:
    active_index_directory: /loki/tsdb-index
    cache_location: /loki/tsdb-cache
  filesystem:
    directory: /loki/chunks

limits_config:
  allow_structured_metadata: true
  volume_enabled: true

compactor:
  working_directory: /loki/compactor
  shared_store: filesystem
CFG

apply_k8s statefulset loki observability << 'ST'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: loki
  namespace: observability
  labels: {app: loki}
spec:
  replicas: 1
  selector: {matchLabels: {app: loki}}
  serviceName: loki
  template:
    metadata: {labels: {app: loki}}
    spec:
      containers:
      - name: loki
        image: grafana/loki:3.4.2
        args:
        - -config.file=/etc/loki/local-config.yaml
        ports: [{containerPort: 3100}]
        readinessProbe:
          httpGet: {path: /ready, port: 3100}
          initialDelaySeconds: 10
          periodSeconds: 5
        volumeMounts:
        - {name: config, mountPath: /etc/loki}
        - {name: data, mountPath: /loki}
      volumes:
      - name: config
        configMap: {name: loki-config}
  volumeClaimTemplates:
  - metadata: {name: data}
    spec:
      accessModes: [\"ReadWriteOnce\"]
      resources: {requests: {storage: 10Gi}}
ST

apply_k8s service loki observability << 'ST'
apiVersion: v1
kind: Service
metadata: {name: loki, namespace: observability}
spec:
  ports: [{port: 3100, targetPort: 3100}]
  selector: {app: loki}
ST

# ══════════════════════════════════════════════════════════
# TEMPO
# ══════════════════════════════════════════════════════════
step \"Deploying Tempo...\"

cfgmap tempo-config observability tempo.yaml /tmp/tempo.yaml << 'CFG'
server:
  http_listen_port: 3200

distributor:
  receivers:
    otlp:
      protocols:
        grpc:
          endpoint: 0.0.0.0:4317
        http:
          endpoint: 0.0.0.0:4318

ingester:
  trace_idle_period: 10s
  max_block_duration: 5m

compactor:
  compaction:
    block_retention: 48h

storage:
  trace:
    backend: local
    local:
      path: /var/tempo/traces
    wal:
      path: /var/tempo/wal
CFG

apply_k8s statefulset tempo observability << 'ST'
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: tempo
  namespace: observability
  labels: {app: tempo}
spec:
  replicas: 1
  selector: {matchLabels: {app: tempo}}
  serviceName: tempo
  template:
    metadata: {labels: {app: tempo}}
    spec:
      containers:
      - name: tempo
        image: grafana/tempo:2.7.0
        args:
        - -config.file=/etc/tempo.yaml
        ports:
        - {containerPort: 3200, name: http}
        - {containerPort: 4317, name: otlp-grpc}
        - {containerPort: 4318, name: otlp-http}
        readinessProbe:
          httpGet: {path: /ready, port: 3200}
          initialDelaySeconds: 10
          periodSeconds: 5
        volumeMounts:
        - {name: config, mountPath: /etc/tempo.yaml, subPath: tempo.yaml}
        - {name: data, mountPath: /var/tempo}
      volumes:
      - name: config
        configMap: {name: tempo-config}
  volumeClaimTemplates:
  - metadata: {name: data}
    spec:
      accessModes: [\"ReadWriteOnce\"]
      resources: {requests: {storage: 10Gi}}
ST

apply_k8s service tempo observability << 'ST'
apiVersion: v1
kind: Service
metadata: {name: tempo, namespace: observability}
spec:
  ports:
  - {port: 3200, targetPort: 3200, name: http}
  - {port: 4317, targetPort: 4317, name: otlp-grpc}
  - {port: 4318, targetPort: 4318, name: otlp-http}
  selector: {app: tempo}
ST

# ══════════════════════════════════════════════════════════
# GRAFANA
# ══════════════════════════════════════════════════════════
step \"Deploying Grafana...\"

cfgmap grafana-ds observability datasources.yml /tmp/gf-ds.yml << 'CFG'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus.observability.svc.cluster.local:9090
    isDefault: true
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki.observability.svc.cluster.local:3100
    editable: true

  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo.observability.svc.cluster.local:3200
    editable: true
    jsonData:
      httpMethod: GET
      serviceMap:
        datasourceUid: prometheus
CFG

# ══════════════════════════════════════════════════════════
# GRAFANA
# ══════════════════════════════════════════════════════════
step \"Deploying Grafana...\"

cfgmap grafana-ds observability datasources.yml /tmp/gf-ds.yml << 'CFG'
apiVersion: 1

datasources:
  - name: Prometheus
    type: prometheus
    uid: prometheus
    access: proxy
    url: http://prometheus.observability.svc.cluster.local:9090
    isDefault: true
    editable: true

  - name: Loki
    type: loki
    access: proxy
    url: http://loki.observability.svc.cluster.local:3100
    editable: true

  - name: Tempo
    type: tempo
    uid: tempo
    access: proxy
    url: http://tempo.observability.svc.cluster.local:3200
    editable: true
    jsonData:
      httpMethod: GET
      serviceMap:
        datasourceUid: prometheus
CFG

apply_k8s deployment grafana observability << 'ST'
apiVersion: apps/v1
kind: Deployment
metadata:
  name: grafana
  namespace: observability
  labels: {app: grafana}
spec:
  replicas: 1
  selector: {matchLabels: {app: grafana}}
  template:
    metadata: {labels: {app: grafana}}
    spec:
      containers:
      - name: grafana
        image: grafana/grafana:12.4.4
        env:
        - name: GF_SECURITY_ADMIN_USER
          value: admin
        - name: GF_USERS_ALLOW_SIGN_UP
          value: \"false\"
        ports: [{containerPort: 3000}]
        readinessProbe:
          httpGet: {path: /api/health, port: 3000}
          initialDelaySeconds: 15
          periodSeconds: 10
        volumeMounts:
        - {name: datasources, mountPath: /etc/grafana/provisioning/datasources}
        - {name: data, mountPath: /var/lib/grafana}
      volumes:
      - name: datasources
        configMap:
          name: grafana-ds
          items:
          - {key: datasources.yml, path: datasources.yml}
      - {name: data, persistentVolumeClaim: {claimName: grafana-data}}
ST

kubectl set env deployment/grafana -n observability \
  GF_SECURITY_ADMIN_PASSWORD=\"\$(get_secret grafana-credentials observability admin-password)\" \
  GF_SERVER_ROOT_URL=\"https://\$DOMAIN/panel\" \
  GF_SERVER_SERVE_FROM_SUB_PATH=true \
  2>/dev/null || true
ok \"Grafana env vars\"

# PVC aparte para evitar recreación del deployment por size change
cat << ST | kubectl apply -f - 2>/dev/null && ok \"PVC grafana-data\" || skip \"PVC grafana-data\"
apiVersion: v1
kind: PersistentVolumeClaim
metadata: {name: grafana-data, namespace: observability}
spec:
  accessModes: [\"ReadWriteOnce\"]
  resources: {requests: {storage: 5Gi}}
ST

apply_k8s service grafana observability << 'ST'
apiVersion: v1
kind: Service
metadata: {name: grafana, namespace: observability}
spec:
  ports: [{port: 3000, targetPort: 3000}]
  selector: {app: grafana}
ST

apply_k8s ingress grafana observability << ST
apiVersion: networking.k8s.io/v1
kind: Ingress
metadata:
  name: grafana
  namespace: observability
  annotations:
    cert-manager.io/cluster-issuer: letsencrypt-prod
    traefik.ingress.kubernetes.io/router.middlewares: ticket-monster-redirect-to-https@kubernetescrd
spec:
  ingressClassName: traefik
  tls:
  - hosts:
    - \${DOMAIN}
    secretName: \$(echo \"\${DOMAIN}\" | tr '.' '-')-tls
  rules:
  - host: \${DOMAIN}
    http:
      paths:
      - path: /panel
        pathType: Prefix
        backend:
          service:
            name: grafana
            port:
              number: 3000
ST

# ══════════════════════════════════════════════════════════
# TRAEFIK MIDDLEWARES
# ══════════════════════════════════════════════════════════
step \"Creando Traefik Middlewares...\"

apply_k8s middleware redirect-to-https ticket-monster << 'ST'
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: redirect-to-https
  namespace: ticket-monster
spec:
  redirectScheme:
    scheme: https
    permanent: true
ST

apply_k8s middleware ticket-monster-rate-limit ticket-monster << 'ST'
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: ticket-monster-rate-limit
  namespace: ticket-monster
spec:
  rateLimit:
    average: 100
    burst: 200
    period: 1s
ST

apply_k8s middleware ticket-monster-secure-headers ticket-monster << 'ST'
apiVersion: traefik.io/v1alpha1
kind: Middleware
metadata:
  name: ticket-monster-secure-headers
  namespace: ticket-monster
spec:
  headers:
    browserXssFilter: true
    contentTypeNosniff: true
    frameDeny: true
    stsSeconds: 63072000
    stsIncludeSubdomains: true
    stsPreload: true
    forceSTSHeader: true
    customFrameOptionsValue: DENY
ST

# ══════════════════════════════════════════════════════════
echo \"\"
echo -e \"\\033[1;32m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\033[0m\"
echo -e \"\\033[1;32m  ✓ Infraestructura completada\\033[0m\"
echo -e \"\\033[1;32m  Dominio: https://\${DOMAIN}\\033[0m\"
echo -e \"\\033[1;32m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\033[0m\"
echo \"\"
echo \"  Service endpoints:\"
echo \"    PostgreSQL: postgresql.infrastructure.svc.cluster.local:5432\"
echo \"    MongoDB:    mongodb.infrastructure.svc.cluster.local:27017\"
echo \"    Redis:      redis.infrastructure.svc.cluster.local:6379\"
echo \"    Redpanda:   redpanda.infrastructure.svc.cluster.local:9092\"
echo \"    Keycloak:   keycloak.infrastructure.svc.cluster.local:8080\"
echo \"    Grafana:    grafana.observability.svc.cluster.local:3000\"
echo \"\"
echo -e \"\\033[1;36m  Ver contraseña de Grafana:\\033[0m\"
echo -e \"\\033[1;36m  kubectl get secret grafana-credentials -n observability -o jsonpath='{.data.admin-password}' | base64 -d\\033[0m\"
echo -e \"\\033[1;36m  kubectl get secrets -n infrastructure\\033[0m\"
INFRA_SCRIPT
bash /tmp/infra-$$.sh '$DOMAIN'
rm -f /tmp/infra-$$.sh"