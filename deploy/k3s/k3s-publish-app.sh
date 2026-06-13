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
CHART_DIR="$SCRIPT_DIR/charts/ticketmonster"

echo "==> Deploying ticketmonster (tag: ${TAG}) to ${HOST}..."

ssh "$USER@$HOST" "rm -rf /tmp/ticketmonster-chart && mkdir -p /tmp/ticketmonster-chart"
scp -r "$CHART_DIR"/* "$USER@$HOST:/tmp/ticketmonster-chart/"

ssh -t "$USER@$HOST" "cat > /tmp/app-$$.sh << 'APP_SCRIPT'
#!/bin/bash
set -euo pipefail
DOMAIN=\$1
TAG=\$2

step() { echo -e \"\\n\\033[1;34m▶ \$1\\033[0m\"; }
ok()   { echo -e \"\\033[1;32m  ✓ \$1\\033[0m\"; }

trap 'echo -e \"\\n\\033[1;31m✗ Error en la línea \$LINENO\\033[0m\"' ERR

export KUBECONFIG=\$HOME/.kube/config

step \"Installing/upgrading ticketmonster Helm chart (tag: \${TAG})...\"

helm upgrade --install ticketmonster /tmp/ticketmonster-chart \
    --namespace ticket-monster \
    --set image.tag=\"\${TAG}\" \
    --set ingress.host=\"\${DOMAIN}\" \
    --wait --timeout 5m

ok \"Helm: ticketmonster (tag: \${TAG})\"

step \"Waiting for pods to be ready...\"
kubectl wait --namespace ticket-monster \
    --for=condition=ready pod \
    --selector app=ticketmonster \
    --timeout=120s

ok \"Pods ready\"

step \"Smoke test...\"
for i in \$(seq 1 12); do
    HTTP_CODE=\$(curl -s -o /dev/null -w \"%{http_code}\" http://localhost:8082/actuator/health 2>/dev/null || echo \"000\")
    if [ \"\$HTTP_CODE\" = \"200\" ]; then break; fi
    sleep 5
done
if [ \"\$HTTP_CODE\" = \"200\" ]; then
    ok \"Health check: \${HTTP_CODE}\"
else
    echo -e \"  \\033[1;31m✗ Health check: \${HTTP_CODE} (tras 60s)\\033[0m\"
fi

rm -rf /tmp/ticketmonster-chart

echo \"\"
echo -e \"\\033[1;32m  ✓ ticketmonster deployed (tag: \${TAG})\\033[0m\"
echo -e \"\\033[1;32m  URL: https://\${DOMAIN}\\033[0m\"
APP_SCRIPT
bash /tmp/app-$$.sh '$DOMAIN' '$TAG'
rm -f /tmp/app-$$.sh"
