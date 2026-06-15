#!/bin/bash
set -euo pipefail

TARGET="${1:-}"
DOMAIN="${2:-}"

if [ -z "$TARGET" ] || [ -z "$DOMAIN" ]; then
  echo "Uso: $0 <usuario@host> <dominio>"
  echo "Ejemplo: $0 janrax@123.112.223.334 janrax.es"
  exit 1
fi

USER=$(echo "$TARGET" | cut -d'@' -f1)
HOST=$(echo "$TARGET" | cut -d'@' -f2)

SECRET_NAME=$(echo "$DOMAIN" | tr '.' '-')-tls

ssh -t "$USER@$HOST" "cat > /tmp/provision-$$.sh << 'PROVISION_SCRIPT'
#!/bin/bash
set -euo pipefail
DOMAIN=\$1
SECRET_NAME=\$2

step() { echo -e \"\\n\\033[1;34m▶ \$1\\033[0m\"; }
ok()   { echo -e \"\\033[1;32m  ✓ \$1\\033[0m\"; }
skip() { echo -e \"\\033[1;33m  ⊘ \$1 (ya existe)\\033[0m\"; }

trap 'echo -e \"\\n\\033[1;31m✗ Error en la línea \$LINENO\\033[0m\"' ERR

# 0. Verificar permisos sudo
if ! id -nG | grep -qw sudo; then
  echo -e \"\\033[1;31m✗ El usuario \$USER no tiene permisos sudo\\033[0m\"
  echo -e \"\\n\\033[1;33mConéctate como root y ejecuta:\\033[0m\"
  echo -e \"  adduser \$USER\"
  echo -e \"  usermod -aG sudo \$USER\"
  exit 1
fi

# 1. Sistema
step \"Actualizando sistema e instalando dependencias...\"
sudo apt update -qq && sudo apt upgrade -y -qq && sudo apt install -y -qq curl wget git tar
ok \"Sistema actualizado\"

# 2. K3s
if command -v k3s &>/dev/null; then
  skip \"K3s\"
else
  step \"Instalando K3s...\"
  curl -sfL https://get.k3s.io | sh -
  ok \"K3s instalado\"
fi

mkdir -p \$HOME/.kube
sudo cp /etc/rancher/k3s/k3s.yaml \$HOME/.kube/config
sudo chown \$USER:\$USER \$HOME/.kube/config
chmod 600 \$HOME/.kube/config
export KUBECONFIG=\$HOME/.kube/config

echo \"  Esperando nodo Ready...\"
until kubectl get nodes | grep -q \"Ready\"; do sleep 2; done
ok \"Nodo Ready\"

# 3. Helm
if command -v helm &>/dev/null; then
  skip \"Helm\"
else
  step \"Instalando Helm...\"
  curl -fsSL https://raw.githubusercontent.com/helm/helm/main/scripts/get-helm-3 | bash
  ok \"Helm instalado\"
fi

# 4. k9s
if command -v k9s &>/dev/null; then
  skip \"k9s\"
else
  step \"Instalando k9s...\"
  K9S_VERSION=\$(curl -s https://api.github.com/repos/derailed/k9s/releases/latest | grep tag_name | cut -d '\"' -f 4)
  wget -q https://github.com/derailed/k9s/releases/download/\${K9S_VERSION}/k9s_Linux_amd64.tar.gz
  tar -xzf k9s_Linux_amd64.tar.gz k9s
  sudo mv k9s /usr/local/bin/
  rm k9s_Linux_amd64.tar.gz
  ok \"k9s instalado\"
fi

# 5. Cert-Manager
if kubectl get namespace cert-manager &>/dev/null; then
  skip \"Cert-Manager\"
else
  step \"Instalando Cert-Manager...\"
  kubectl apply -f https://github.com/cert-manager/cert-manager/releases/download/v1.14.4/cert-manager.yaml
  echo \"  Esperando pods de Cert-Manager...\"
  while [ \"\$(kubectl get pods -n cert-manager --no-headers 2>/dev/null | wc -l)\" -eq 0 ]; do sleep 2; done
  kubectl wait --namespace cert-manager --for=condition=ready pod --all --timeout=120s
  ok \"Cert-Manager listo\"
fi

# 6. ClusterIssuer
if kubectl get clusterissuer letsencrypt-prod &>/dev/null; then
  skip \"ClusterIssuer\"
else
  step \"Creando ClusterIssuer Let's Encrypt...\"
  cat << YAML | kubectl apply -f -
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: admin@\${DOMAIN}
    privateKeySecretRef:
      name: letsencrypt-prod-account-key
    solvers:
    - http01:
        ingress:
          ingressClassName: traefik
YAML
  ok \"ClusterIssuer creado\"
fi

echo -e \"\\033[1;32m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\033[0m\"
echo -e \"\\033[1;32m  ✓ Aprovisionamiento completado\\033[0m\"
echo -e \"\\033[1;32m  Dominio: https://\${DOMAIN}\\033[0m\"
echo -e \"\\033[1;32m━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━\\033[0m\"
PROVISION_SCRIPT
bash /tmp/provision-$$.sh '$DOMAIN' '$SECRET_NAME'
rm -f /tmp/provision-$$.sh"
