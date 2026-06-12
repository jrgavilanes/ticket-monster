#!/usr/bin/env bash
set -euo pipefail

TARGET="${1:-}"
DOMAIN="${2:-}"
TAG="${3:-latest}"

if [ -z "$TARGET" ] || [ -z "$DOMAIN" ]; then
    echo "Uso: $0 <usuario@host> <dominio> [version]"
    echo ""
    echo "Despliega la aplicación completa en un VPS con K3s:"
    echo "  1. k3s-provision.sh  — K3s, cert-manager, Let's Encrypt"
    echo "  2. k3s-infrastructure.sh — PostgreSQL, MongoDB, Redis, Redpanda, Keycloak, Observabilidad"
    echo "  3. k3s-app.sh — Helm install del monolith"
    echo ""
    echo "Ejemplo: $0 janrax@janrax.es janrax.es 1.0.0"
    exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "==============================================="
echo "  Ticket Monster — Full Deployment"
echo "  Target: $TARGET"
echo "  Domain: $DOMAIN"
echo "  Tag:    $TAG"
echo "==============================================="
echo ""

# 1. K3s provisioning
echo "▶ Step 1/3: K3s provisioning"
"$SCRIPT_DIR/k3s-provision.sh" "$TARGET" "$DOMAIN"
echo ""

# 2. Infrastructure
echo "▶ Step 2/3: Infrastructure"
"$SCRIPT_DIR/k3s-infrastructure.sh" "$TARGET" "$DOMAIN"
echo ""

# 3. Application
echo "▶ Step 3/3: Application"
"$SCRIPT_DIR/k3s-app.sh" "$TARGET" "$DOMAIN" "$TAG"
echo ""
echo "==============================================="
echo "  ✓ Deployment complete"
echo "  https://$DOMAIN"
echo "==============================================="
