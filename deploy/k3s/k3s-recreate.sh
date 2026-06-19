#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 -u <user@host> -d <domain>"
    echo "  Backs up TLS certs, destroys infra/obs namespaces, re-provisions, restores certs."
    echo "  No new Let's Encrypt requests — certs are preserved."
    echo ""
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

if [ -z "${TARGET:-}" ] || [ -z "${DOMAIN:-}" ]; then usage; fi

USER=$(echo "$TARGET" | cut -d'@' -f1)
HOST=$(echo "$TARGET" | cut -d'@' -f2)
TLS_SECRET=$(echo "$DOMAIN" | tr '.' '-')-tls
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="/tmp/tls-backup-$$"

echo "======================================================================"
echo "  TLS-Preserving Recreate: $DOMAIN"
echo "======================================================================"

# ── Phase 1: Backup TLS secrets from VPS ──
echo ""
echo "==> [1/4] Backing up TLS secrets from VPS..."

ssh "$USER@$HOST" "
set -e
export KUBECONFIG=\$HOME/.kube/config
mkdir -p '$BACKUP_DIR'

for ns in infrastructure observability; do
    if kubectl get secret '$TLS_SECRET' -n \$ns &>/dev/null; then
        kubectl get secret '$TLS_SECRET' -n \$ns -o yaml > '$BACKUP_DIR/tls-'\"\$ns\"'.yaml'
        echo \"  ✓ Saved: \$ns\"
    else
        echo \"  ⊘ No TLS secret in \$ns\"
    fi
done
echo \"  Backups at: $BACKUP_DIR\"
ls -la '$BACKUP_DIR/'
"

# ── Phase 2: Destroy namespaces ──
echo ""
echo "==> [2/4] Deleting namespaces (infrastructure, observability, ticket-monster)..."

ssh "$USER@$HOST" "
set -e
export KUBECONFIG=\$HOME/.kube/config
kubectl delete namespace infrastructure --ignore-not-found --wait --timeout=60s || true
kubectl delete namespace observability --ignore-not-found --wait --timeout=60s || true
kubectl delete namespace ticket-monster --ignore-not-found --wait --timeout=60s || true
echo '  All namespaces removed.'
"

# ── Phase 3: Re-provision infrastructure ──
echo ""
echo "==> [3/4] Re-provisioning infrastructure..."

bash "$SCRIPT_DIR/k3s-infrastructure.sh" -u "$TARGET" -d "$DOMAIN"

# ── Phase 4: Restore TLS secrets ──
echo ""
echo "==> [4/4] Restoring TLS secrets..."

ssh "$USER@$HOST" "
set -e
export KUBECONFIG=\$HOME/.kube/config

for ns in infrastructure observability; do
    BACKUP_FILE='$BACKUP_DIR/tls-'\$ns'.yaml'
    if [ -f \"\$BACKUP_FILE\" ]; then
        echo \"  Restoring to: \$ns\"
        # Remove the freshly-issued cert secret (if any)
        kubectl delete secret '$TLS_SECRET' -n \$ns --ignore-not-found 2>/dev/null || true
        # Delete cert-manager Certificate resources so they reconcile from Ingress
        kubectl delete certificate -n \$ns --all --ignore-not-found 2>/dev/null || true
        # Restore the old TLS secret
        kubectl apply -f \"\$BACKUP_FILE\" -n \$ns
        echo \"  ✓ TLS restored in \$ns\"
    else
        echo \"  ⊘ No backup for \$ns (fresh cert will be issued)\"
    fi
done

rm -rf '$BACKUP_DIR'
"
echo ""
echo "======================================================================"
echo "  Done! TLS certs preserved. Zero Let's Encrypt requests."
echo "======================================================================"
