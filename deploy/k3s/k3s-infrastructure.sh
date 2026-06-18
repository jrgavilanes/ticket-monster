#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 -u <user@host> -d <domain>"
    echo "Example: $0 -u janrax@janrax.es -d janrax.es"
    exit 1
}

while getopts "u:d:h" opt; do
    case $opt in
        u) TARGET="$OPTARG" ;;
        d) DOMAIN="$OPTARG" ;;
        h) usage ;;
        *) usage ;;
    esac
done

if [ -z "${TARGET:-}" ] || [ -z "${DOMAIN:-}" ]; then
    usage
fi

USER=$(echo "$TARGET" | cut -d'@' -f1)
HOST=$(echo "$TARGET" | cut -d'@' -f2)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHARTS_DIR="$SCRIPT_DIR/charts"

echo "==> Copying charts to ${HOST}..."
scp -r "$CHARTS_DIR" "$USER@$HOST:/tmp/k3s-charts"

ssh -t "$USER@$HOST" "cat > /tmp/infra-$$.sh << 'INFRA_SCRIPT'
#!/bin/bash
set -euo pipefail
DOMAIN=\$1

step() { echo -e \"\\n\\033[1;34m▶ \$1\\033[0m\"; }
ok()   { echo -e \"\\033[1;32m  ✓ \$1\\033[0m\"; }
skip() { echo -e \"\\033[1;33m  ⊘ \$1 (ya existe)\\033[0m\"; }

trap 'echo -e \"\\n\\033[1;31m✗ Error en la línea \$LINENO\\033[0m\"' ERR

export KUBECONFIG=\$HOME/.kube/config
CHARTS=/tmp/k3s-charts

# ── helpers ──────────────────────────────────────────────

gen_secret() {
    local name=\"\$1\" ns=\"\$2\"; shift 2
    if kubectl get secret \"\$name\" -n \"\$ns\" &>/dev/null; then
        skip \"Secret \$name (\$ns)\"
        return 0
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
        | kubectl create -f -
    ok \"Secret \$name: \$from → \$to\"
}

helm_install() {
    local name=\"\$1\" chart=\"\$2\" ns=\"\$3\"; shift 3
    helm upgrade --install \"\$name\" \"\$chart\" \
        --namespace \"\$ns\" \
        --create-namespace \
        \"\$@\" \
        --wait --timeout 3m
    ok \"Helm: \$name (\$ns)\"
}

# ══════════════════════════════════════════════════════════
# NAMESPACES
# ══════════════════════════════════════════════════════════
step \"Creando namespaces...\"
for ns in infrastructure observability ticket-monster; do
    kubectl get namespace "\$ns" &>/dev/null && skip "Namespace \$ns" || kubectl create namespace "\$ns" && ok "Namespace \$ns"
done

# ══════════════════════════════════════════════════════════
# SECRETS
# ══════════════════════════════════════════════════════════
step \"Generando Secrets...\"
gen_secret postgresql-credentials infrastructure postgres-password= ticketmonster-password=
# Ensure POSTGRES_PASSWORD exists with same value as ticketmonster-password
TPW=\$(kubectl get secret postgresql-credentials -n infrastructure -o jsonpath='{.data.ticketmonster-password}')
kubectl patch secret postgresql-credentials -n infrastructure \
    --patch="{\\\"data\\\":{\\\"POSTGRES_PASSWORD\\\":\\\"\${TPW}\\\"}}" 2>/dev/null || true
gen_secret mongodb-credentials infrastructure mongodb-root-password= mongodb-password=
# Ensure MONGO_PASSWORD exists with same value as mongodb-root-password
MROOT=\$(kubectl get secret mongodb-credentials -n infrastructure -o jsonpath='{.data.mongodb-root-password}')
kubectl patch secret mongodb-credentials -n infrastructure \
    --patch="{\\\"data\\\":{\\\"MONGO_PASSWORD\\\":\\\"\${MROOT}\\\"}}" 2>/dev/null || true
gen_secret redis-credentials infrastructure redis-password=
gen_secret keycloak-credentials infrastructure admin-password=
gen_secret redpanda-console-credentials infrastructure admin-password=
gen_secret grafana-credentials observability admin-password= oauth-client-secret=grafana-client-secret
# Patch oauth-client-secret for upgrades (gen_secret skips if secret already exists)
kubectl patch secret grafana-credentials -n observability \
    --patch='{"data":{"oauth-client-secret":"Z3JhZmFuYS1jbGllbnQtc2VjcmV0"}}' 2>/dev/null || true

step \"Copiando Secrets a ticket-monster...\"
copy_secret postgresql-credentials infrastructure ticket-monster
copy_secret mongodb-credentials infrastructure ticket-monster
copy_secret redis-credentials infrastructure ticket-monster

# ══════════════════════════════════════════════════════════
# INFRASTRUCTURE SERVICES
# ══════════════════════════════════════════════════════════
step \"Deploying PostgreSQL...\"
helm_install postgresql \"\$CHARTS/postgresql\" infrastructure

step \"Deploying MongoDB...\"
helm_install mongodb \"\$CHARTS/mongodb\" infrastructure

step \"Deploying Redis...\"
helm_install redis \"\$CHARTS/redis\" infrastructure

step \"Deploying Redpanda...\"
helm_install redpanda \"\$CHARTS/redpanda\" infrastructure

step \"Deploying Redpanda Console...\"
helm_install redpanda-console \"\$CHARTS/redpanda-console\" infrastructure \
    --set domain=\"redpanda.\$DOMAIN\"

step \"Deploying Keycloak...\"
helm_install keycloak \"\$CHARTS/keycloak\" infrastructure \
    --set domain=\"\$DOMAIN\"

step \"Configurando cliente Grafana en Keycloak...\"
# Kill any stale port-forward from previous runs
fuser -k 18080/tcp 2>/dev/null || true
sleep 1
kubectl port-forward -n infrastructure svc/keycloak 18080:8080 &
KC_PF=\$!
sleep 5
until curl -s http://localhost:18080/auth/realms/master >/dev/null 2>&1; do
    echo \"  Esperando a que Keycloak esté listo...\"; sleep 5
done

KC_ADMIN=\$(kubectl get secret keycloak-credentials -n infrastructure -o jsonpath='{.data.admin-password}' | base64 -d)
KC_TOKEN=\$(curl -s -X POST http://localhost:18080/auth/realms/master/protocol/openid-connect/token \
    -d 'client_id=admin-cli' \
    -d 'username=admin' \
    -d \"password=\$KC_ADMIN\" \
    -d 'grant_type=password' 2>/dev/null | jq -r '.access_token')

KC_CLIENT_ID=\$(curl -s -H \"Authorization: Bearer \$KC_TOKEN\" \
    http://localhost:18080/auth/admin/realms/ticket-monster/clients?clientId=grafana 2>/dev/null | jq -r '.[0].id // empty')
if [ -n \"\$KC_CLIENT_ID\" ]; then
    KC_CLIENT_JSON=\$(curl -s -H \"Authorization: Bearer \$KC_TOKEN\" \
        http://localhost:18080/auth/admin/realms/ticket-monster/clients/\$KC_CLIENT_ID)
    UPDATED_JSON=\$(echo \"\$KC_CLIENT_JSON\" | jq -c --arg uri \"https://\$DOMAIN/panel/login/generic_oauth\" '.redirectUris += [\$uri] | .redirectUris |= unique')
    curl -s -X PUT \
        -H \"Authorization: Bearer \$KC_TOKEN\" \
        -H 'Content-Type: application/json' \
        http://localhost:18080/auth/admin/realms/ticket-monster/clients/\$KC_CLIENT_ID \
        -d \"\$UPDATED_JSON\" >/dev/null
    ok \"Grafana client updated: https://\$DOMAIN/panel/login/generic_oauth\"
else
    HTTP_CODE=\$(curl -s -o /dev/null -w '%{http_code}' -X POST \
        -H \"Authorization: Bearer \$KC_TOKEN\" \
        -H 'Content-Type: application/json' \
        http://localhost:18080/auth/admin/realms/ticket-monster/clients \
        -d "{\"clientId\":\"grafana\",\"publicClient\":false,\"standardFlowEnabled\":true,\"directAccessGrantsEnabled\":false,\"redirectUris\":[\"http://localhost:3000/login/generic_oauth\",\"https://$DOMAIN/panel/login/generic_oauth\"],\"webOrigins\":[\"+\"],\"secret\":\"grafana-client-secret\"}")
    if [ \"\$HTTP_CODE\" = \"201\" ]; then
        ok \"Grafana client created: https://\$DOMAIN/panel/login/generic_oauth\"
    else
        echo \"  \\033[1;31m✗ Failed to create Grafana client (HTTP \$HTTP_CODE)\\033[0m\"
    fi
fi

kill \$KC_PF 2>/dev/null || true
fuser -k 18080/tcp 2>/dev/null || true

# ══════════════════════════════════════════════════════════
# OBSERVABILITY
# ══════════════════════════════════════════════════════════
step \"Deploying Prometheus...\"
helm_install prometheus \"\$CHARTS/prometheus\" observability

step \"Deploying Loki...\"
helm_install loki \"\$CHARTS/loki\" observability

step \"Deploying Promtail...\"
helm_install promtail \"\$CHARTS/promtail\" observability

step \"Deploying Tempo...\"
helm_install tempo \"\$CHARTS/tempo\" observability

step \"Deploying Grafana...\"
helm_install grafana \"\$CHARTS/grafana\" observability \
    --set domain=\"\$DOMAIN\"

# ══════════════════════════════════════════════════════════
# TRAEFIK MIDDLEWARES
# ══════════════════════════════════════════════════════════
step \"Creando Traefik Middlewares...\"
helm_install traefik-middlewares \"\$CHARTS/traefik-middlewares\" ticket-monster

# ══════════════════════════════════════════════════════════
echo \"\"
echo -e \"\\033[1;32m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\033[0m\"
echo -e \"\\033[1;32m  ✓ Infraestructura completada\\033[0m\"
echo -e \"\\033[1;32m  Dominio: https://\${DOMAIN}\\033[0m\"
echo -e \"\\033[1;32m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\033[0m\"
echo \"\"
echo \"  URLs accesibles:\"
echo \"    Keycloak:        https://\${DOMAIN}/auth\"
echo \"    Grafana:         https://\${DOMAIN}/panel\"
echo \"    Redpanda Console: https://redpanda.\${DOMAIN}\"
echo \"\"
echo \"  Grafana → Keycloak OAuth:\"
echo \"    Login: https://\${DOMAIN}/panel > Sign in with Keycloak\"
echo \"    Usuarios: admin/admin (Grafana Admin)  |  user/user (Grafana Editor)\"
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
echo -e \"\\033[1;36m  Ver contraseña de Redpanda Console:\\033[0m\"
echo -e \"\\033[1;36m  kubectl get secret redpanda-console-credentials -n infrastructure -o jsonpath='{.data.admin-password}' | base64 -d\\033[0m\"
echo -e \"\\033[1;36m  kubectl get secrets -n infrastructure\\033[0m\"
INFRA_SCRIPT
bash /tmp/infra-$$.sh '$DOMAIN'
rm -f /tmp/infra-$$.sh
rm -rf /tmp/k3s-charts"
