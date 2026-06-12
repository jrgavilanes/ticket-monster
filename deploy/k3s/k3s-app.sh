#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:-}"
DOMAIN="${2:-}"
TAG="${3:-latest}"

if [ -z "$TARGET" ] || [ -z "$DOMAIN" ]; then
    echo "Uso: $0 <usuario@host> <dominio> [version]"
    echo "Ejemplo: $0 janrax@janrax.es janrax.es 1.0.0"
    echo "         $0 janrax@janrax.es janrax.es       (usa 'latest')"
    exit 1
fi

USER=$(echo "$TARGET" | cut -d'@' -f1)
HOST=$(echo "$TARGET" | cut -d'@' -f2)

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CHART_DIR="$SCRIPT_DIR/../charts/ticketmonster"

echo "==> Deploying ticketmonster (tag: ${TAG}) to ${HOST}..."

scp -r "$CHART_DIR" "$USER@$HOST:/tmp/ticketmonster-chart"

ssh -t "$USER@$HOST" "cat > /tmp/app-$$.sh << 'APP_SCRIPT'
#!/bin/bash
set -euo pipefail
DOMAIN=\$1
TAG=\$2

export KUBECONFIG=\$HOME/.kube/config

echo \"==> Installing/upgrading ticketmonster Helm chart (tag: \${TAG})...\"

helm upgrade --install ticketmonster /tmp/ticketmonster-chart \
    --namespace ticket-monster \
    --set image.tag=\"\${TAG}\" \
    --set ingress.host=\"\${DOMAIN}\" \
    --wait --timeout 5m

echo \"==> Waiting for pods to be ready...\"
kubectl wait --namespace ticket-monster \
    --for=condition=ready pod \
    --selector app=ticketmonster \
    --timeout=120s

echo \"==> Smoke test...\"
sleep 5
HTTP_CODE=\$(curl -s -o /dev/null -w \"%{http_code}\" http://localhost:8082/actuator/health)
if [ \"\$HTTP_CODE\" = \"200\" ]; then
    echo -e \"  \\033[1;32m✓ Health check: \${HTTP_CODE}\\033[0m\"
else
    echo -e \"  \\033[1;31m✗ Health check: \${HTTP_CODE}\\033[0m\"
fi

rm -rf /tmp/ticketmonster-chart

echo \"\"
echo -e \"\\033[1;32m  ✓ ticketmonster deployed (tag: \${TAG})\\033[0m\"
echo -e \"\\033[1;32m  URL: https://\${DOMAIN}\\033[0m\"
APP_SCRIPT
bash /tmp/app-$$.sh '$DOMAIN' '$TAG'
rm -f /tmp/app-$$.sh"