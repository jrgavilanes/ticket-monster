#!/usr/bin/env bash
set -euo pipefail

usage() {
    echo "Usage: $0 -u <user@host> -d <domain>"
    echo ""
    echo "Builds, deploys, and tests the application on K3s."
    echo ""
    echo "Options:"
    echo "  -u  SSH user@host (e.g., janrax@janrax.es)"
    echo "  -d  Domain name for the cluster"
    echo "  -t  Run tests only (skip build and deploy)"
    echo "  -h  Show this help message"
    exit 1
}

RUN_TESTS_ONLY=false

while getopts "u:d:th" opt; do
    case $opt in
        u) SSH_TARGET="$OPTARG" ;;
        d) DOMAIN="$OPTARG" ;;
        t) RUN_TESTS_ONLY=true ;;
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
REGISTRY="${DOMAIN}:5000"

if [[ "${RUN_TESTS_ONLY}" == "false" ]]; then
    echo "==> Building ticketmonster Docker image..."
    docker build -t "${REGISTRY}/ticketmonster:latest" \
        -f "${PROJECT_DIR}/backend/ticketmonster/Dockerfile" \
        "${PROJECT_DIR}/backend/ticketmonster"

    echo "==> Building api-gateway Docker image..."
    docker build -t "${REGISTRY}/api-gateway:latest" \
        -f "${PROJECT_DIR}/backend/api-gateway/Dockerfile" \
        "${PROJECT_DIR}/backend/api-gateway"

    echo "==> Pushing images to registry..."
    docker push "${REGISTRY}/ticketmonster:latest"
    docker push "${REGISTRY}/api-gateway:latest"

    echo ""
    echo "==> Deploying ticketmonster..."
    helm upgrade --install ticketmonster "${PROJECT_DIR}/deploy/charts/ticketmonster" \
        --namespace ticket-monster \
        --set image.repository="${REGISTRY}/ticketmonster" \
        --set image.tag=latest \
        --set ingress.host="api.${DOMAIN}" \
        --wait --timeout 5m

    echo ""
    echo "==> Deploying api-gateway..."
    helm upgrade --install api-gateway "${PROJECT_DIR}/deploy/charts/api-gateway" \
        --namespace ticket-monster \
        --set image.repository="${REGISTRY}/api-gateway" \
        --set image.tag=latest \
        --set ingress.host="gateway.${DOMAIN}" \
        --set monolithUri="http://ticketmonster.ticket-monster.svc.cluster.local:8082" \
        --wait --timeout 5m

    echo ""
    echo "==> Waiting for all pods to be ready..."
    kubectl wait --for=condition=ready pod -l app=ticketmonster -n ticket-monster --timeout=120s
    kubectl wait --for=condition=ready pod -l app=api-gateway -n ticket-monster --timeout=120s
    echo "==> All pods ready"
fi

echo ""
echo "==> Running k6 load tests..."
K6_TESTS_DIR="${PROJECT_DIR}/deploy/tests/k6"

if [[ -d "${K6_TESTS_DIR}" ]]; then
    for test_file in "${K6_TESTS_DIR}"/*.js; do
        if [[ -f "${test_file}" ]]; then
            test_name=$(basename "${test_file}" .js)
            echo "==> Running test: ${test_name}"
            docker run --rm --network host \
                -v "${K6_TESTS_DIR}:/scripts" \
                grafana/k6 run "/scripts/${test_name}.js" \
                --out prometheus=namespace=ticket-monster || {
                    echo "WARNING: Test ${test_name} failed"
                }
        fi
    done
else
    echo "==> No k6 tests found at ${K6_TESTS_DIR}, skipping"
fi

echo ""
echo "==> Services deployment complete!"
echo "    Domain:      ${DOMAIN}"
echo "    API Gateway: https://gateway.${DOMAIN}"
echo "    Monolith:    https://api.${DOMAIN}"
echo ""
kubectl get pods -n ticket-monster
