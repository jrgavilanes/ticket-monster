#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 -u <user@host> -d <domain>"
    echo ""
    echo "Provisions a Debian 12 VPS with K3s, Helm, and k9s."
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

echo "==> Provisioning K3s on ${SSH_TARGET} (domain: ${DOMAIN})"

ssh "${SSH_TARGET}" bash <<'REMOTE_SCRIPT'
set -euo pipefail

echo "==> Updating system packages..."
apt-get update -qq && apt-get upgrade -y -qq

if command -v k3s &>/dev/null; then
    echo "==> K3s already installed: $(k3s --version)"
else
    echo "==> Installing K3s..."
    curl -sfL https://get.k3s.io | sh -s - server \
        --disable traefik \
        --disable servicelb \
        --write-kubeconfig-mode 644 \
        --tls-san "$(hostname -f)" \
        --tls-san "$(curl -s ifconfig.me)"
    echo "==> K3s installed: $(k3s --version)"
fi

if command -v helm &>/dev/null; then
    echo "==> Helm already installed: $(helm version --short)"
else
    echo "==> Installing Helm..."
    curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
    echo "==> Helm installed: $(helm version --short)"
fi

K9S_VERSION="v0.32.7"
if command -v k9s &>/dev/null; then
    echo "==> k9s already installed: $(k9s version --short)"
else
    echo "==> Installing k9s ${K9S_VERSION}..."
    curl -fsSL "https://github.com/derailed/k9s/releases/download/${K9S_VERSION}/k9s_Linux_amd64.tar.gz" \
        | tar xz -C /usr/local/bin k9s
    echo "==> k9s installed: $(k9s version --short)"
fi

echo "==> K3s cluster status:"
k3s kubectl get nodes
REMOTE_SCRIPT

echo "==> Copying kubeconfig to local machine..."
mkdir -p "${HOME}/.kube"
scp "${SSH_TARGET}:/etc/rancher/k3s/k3s.yaml" "${HOME}/.kube/ticket-monster-config"

SERVER_IP=$(ssh "${SSH_TARGET}" "curl -s ifconfig.me")
sed -i "s|127.0.0.1|${SERVER_IP}|g" "${HOME}/.kube/ticket-monster-config"
chmod 600 "${HOME}/.kube/ticket-monster-config"

export KUBECONFIG="${HOME}/.kube/ticket-monster-config"
echo "==> Kubeconfig saved to ${HOME}/.kube/ticket-monster-config"
echo "==> Set KUBECONFIG=${HOME}/.kube/ticket-monster-config to use this cluster"
echo ""
echo "==> Verifying local kubectl access..."
kubectl --kubeconfig="${HOME}/.kube/ticket-monster-config" get nodes

echo ""
echo "==> K3s provisioning complete!"
echo "    Domain: ${DOMAIN}"
echo "    SSH:    ${SSH_TARGET}"
echo "    KUBECONFIG: ${HOME}/.kube/ticket-monster-config"
