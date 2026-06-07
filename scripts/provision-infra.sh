#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 -u <user@host> -d <domain>"
    echo ""
    echo "Deploys all infrastructure to a K3s cluster via Helm."
    echo ""
    echo "Options:"
    echo "  -u  SSH user@host (e.g., janrax@janrax.es)"
    echo "  -d  Domain name for the cluster"
    echo "  -h  Show this help message"
    exit 1
}

while getopts "u:d:h" opt; do
    case $opt in
        u) SSH_TARGET="$OPTARG" ;;
        d) DOMAIN="$OPTARG" ;;
        h) usage ;;
        *) usage ;;
    esac
done

if [[ -z "${SSH_TARGET:-}" || -z "${DOMAIN:-}" ]]; then
    usage
fi

KUBECONFIG="${HOME}/.kube/ticket-monster-config"
if [[ ! -f "${KUBECONFIG}" ]]; then
    echo "ERROR: kubeconfig not found at ${KUBECONFIG}. Run provision-k3s.sh first."
    exit 1
fi
export KUBECONFIG

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "${SCRIPT_DIR}")"

generate_secret() {
    local name="$1"
    local namespace="$2"
    local key="$3"

    if kubectl get secret "${name}" -n "${namespace}" &>/dev/null; then
        echo "==> Secret ${name} already exists in ${namespace}, skipping"
        return
    fi

    local value
    value=$(openssl rand -hex 32)
    kubectl create secret generic "${name}" \
        --namespace "${namespace}" \
        --from-literal="${key}=${value}"
    echo "==> Created secret ${name} in ${namespace}"
}

echo "==> Adding Helm repositories..."
helm repo add jetstack https://charts.jetstack.io 2>/dev/null || true
helm repo add bitnami https://charts.bitnami.com/bitnami 2>/dev/null || true
helm repo add redpanda https://charts.redpanda.com 2>/dev/null || true
helm repo add grafana https://grafana.github.io/helm-charts 2>/dev/null || true
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts 2>/dev/null || true
helm repo update

echo "==> Creating namespaces..."
for ns in cert-manager infrastructure ticket-monster observability; do
    kubectl create namespace "${ns}" --dry-run=client -o yaml | kubectl apply -f -
done

echo ""
echo "==> [1/7] Deploying cert-manager..."
helm upgrade --install cert-manager jetstack/cert-manager \
    --namespace cert-manager \
    --set crds.enabled=true \
    --wait --timeout 5m

cat <<EOF | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@${DOMAIN}
    privateKeySecretRef:
      name: letsencrypt-prod
    solvers:
      - http01:
          ingress:
            class: nginx
EOF
echo "==> cert-manager deployed"

echo ""
echo "==> [2/7] Deploying PostgreSQL..."
generate_secret postgresql-credentials infrastructure postgres-password
generate_secret postgresql-credentials infrastructure ticketmonster-password

helm upgrade --install postgresql bitnami/postgresql \
    --namespace infrastructure \
    --set auth.postgresPassword="$(kubectl get secret postgresql-credentials -n infrastructure -o jsonpath='{.data.postgres-password}' | base64 -d)" \
    --set auth.database=ticketmonster \
    --set auth.username=ticketmonster \
    --set auth.password="$(kubectl get secret postgresql-credentials -n infrastructure -o jsonpath='{.data.ticketmonster-password}' | base64 -d)" \
    --set primary.persistence.enabled=true \
    --set primary.persistence.size=10Gi \
    --wait --timeout 5m
echo "==> PostgreSQL deployed"

echo ""
echo "==> [3/7] Deploying MongoDB..."
generate_secret mongodb-credentials infrastructure mongodb-root-password
generate_secret mongodb-credentials infrastructure mongodb-password

helm upgrade --install mongodb bitnami/mongodb \
    --namespace infrastructure \
    --set auth.rootPassword="$(kubectl get secret mongodb-credentials -n infrastructure -o jsonpath='{.data.mongodb-root-password}' | base64 -d)" \
    --set auth.usernames[0]=ticketmonster \
    --set auth.passwords[0]="$(kubectl get secret mongodb-credentials -n infrastructure -o jsonpath='{.data.mongodb-password}' | base64 -d)" \
    --set auth.databases[0]=ticketmonster_catalog \
    --set persistence.enabled=true \
    --set persistence.size=10Gi \
    --wait --timeout 5m
echo "==> MongoDB deployed"

echo ""
echo "==> [4/7] Deploying Redis..."
generate_secret redis-credentials infrastructure redis-password

helm upgrade --install redis bitnami/redis \
    --namespace infrastructure \
    --set auth.password="$(kubectl get secret redis-credentials -n infrastructure -o jsonpath='{.data.redis-password}' | base64 -d)" \
    --set architecture=standalone \
    --set master.persistence.enabled=true \
    --set master.persistence.size=5Gi \
    --set master.configmap.notify-keyspace-events=Ex \
    --wait --timeout 5m
echo "==> Redis deployed"

echo ""
echo "==> [5/7] Deploying Redpanda..."
helm upgrade --install redpanda redpanda/redpanda \
    --namespace infrastructure \
    --set statefulset.replicas=1 \
    --set storage.persistentVolume.size=10Gi \
    --set console.enabled=true \
    --wait --timeout 10m
echo "==> Redpanda deployed"

echo ""
echo "==> [6/7] Deploying Keycloak..."
generate_secret keycloak-credentials infrastructure admin-password

helm upgrade --install keycloak bitnami/keycloak \
    --namespace infrastructure \
    --set auth.adminUser=admin \
    --set auth.adminPassword="$(kubectl get secret keycloak-credentials -n infrastructure -o jsonpath='{.data.admin-password}' | base64 -d)" \
    --set postgresql.enabled=false \
    --set externalDatabase.host=postgresql.infrastructure.svc.cluster.local \
    --set externalDatabase.port=5432 \
    --set externalDatabase.user=ticketmonster \
    --set externalDatabase.password="$(kubectl get secret postgresql-credentials -n infrastructure -o jsonpath='{.data.ticketmonster-password}' | base64 -d)" \
    --set externalDatabase.database=keycloak \
    --wait --timeout 5m
echo "==> Keycloak deployed"

echo ""
echo "==> [7/7] Deploying Observability Stack..."

helm upgrade --install prometheus prometheus-community/prometheus \
    --namespace observability \
    --set server.persistentVolume.size=10Gi \
    --set server.retention=7d \
    --wait --timeout 5m
echo "==> Prometheus deployed"

helm upgrade --install loki grafana/loki \
    --namespace observability \
    --set singleBinary.replicas=1 \
    --set singleBinary.persistence.size=10Gi \
    --wait --timeout 5m
echo "==> Loki deployed"

helm upgrade --install tempo grafana/tempo \
    --namespace observability \
    --set persistence.enabled=true \
    --set persistence.size=10Gi \
    --wait --timeout 5m
echo "==> Tempo deployed"

helm upgrade --install grafana grafana/grafana \
    --namespace observability \
    --set persistence.enabled=true \
    --set persistence.size=5Gi \
    --set adminPassword=admin \
    --set datasources."datasources\.yaml".apiVersion=1 \
    --set datasources."datasources\.yaml".datasources[0].name=Prometheus \
    --set datasources."datasources\.yaml".datasources[0].type=prometheus \
    --set datasources."datasources\.yaml".datasources[0].url=http://prometheus-server.observability.svc.cluster.local \
    --set datasources."datasources\.yaml".datasources[0].isDefault=true \
    --set datasources."datasources\.yaml".datasources[1].name=Loki \
    --set datasources."datasources\.yaml".datasources[1].type=loki \
    --set datasources."datasources\.yaml".datasources[1].url=http://loki.observability.svc.cluster.local:3100 \
    --set datasources."datasources\.yaml".datasources[2].name=Tempo \
    --set datasources."datasources\.yaml".datasources[2].type=tempo \
    --set datasources."datasources\.yaml".datasources[2].url=http://tempo.observability.svc.cluster.local:3200 \
    --wait --timeout 5m
echo "==> Grafana deployed"

echo ""
echo "==> Creating application secrets in ticket-monster namespace..."

kubectl get secret postgresql-credentials -n infrastructure -o json \
    | sed 's/"namespace": "infrastructure"/"namespace": "ticket-monster"/' \
    | kubectl apply -f - 2>/dev/null || true

kubectl get secret mongodb-credentials -n infrastructure -o json \
    | sed 's/"namespace": "infrastructure"/"namespace": "ticket-monster"/' \
    | kubectl apply -f - 2>/dev/null || true

kubectl get secret redis-credentials -n infrastructure -o json \
    | sed 's/"namespace": "infrastructure"/"namespace": "ticket-monster"/' \
    | kubectl apply -f - 2>/dev/null || true

echo ""
echo "==> Infrastructure provisioning complete!"
echo "    Domain: ${DOMAIN}"
echo ""
echo "==> Service endpoints:"
echo "    PostgreSQL: postgresql.infrastructure.svc.cluster.local:5432"
echo "    MongoDB:    mongodb.infrastructure.svc.cluster.local:27017"
echo "    Redis:      redis-master.infrastructure.svc.cluster.local:6379"
echo "    Redpanda:   redpanda.infrastructure.svc.cluster.local:9092"
echo "    Keycloak:   keycloak.infrastructure.svc.cluster.local:8080"
echo "    Grafana:    grafana.observability.svc.cluster.local:3000"
