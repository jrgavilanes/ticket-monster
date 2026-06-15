#!/usr/bin/env bash
set -euo pipefail

TAG="${1:-latest}"
REGISTRY="ghcr.io"
IMAGE_NAME="jrgavilanes/ticket-monster"
FULL_IMAGE="${REGISTRY}/${IMAGE_NAME}:${TAG}"

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DOCKER_DIR="${SCRIPT_DIR}/backend/ticketmonster"

echo "==> Running tests..."
(cd "${DOCKER_DIR}" && ./gradlew test --no-daemon)
echo -e "\033[1;32m  ✓ Tests passed\033[0m"

echo "==> Building ${FULL_IMAGE}..."

docker build \
  --network host \
  -t "${FULL_IMAGE}" \
  -f "${DOCKER_DIR}/Dockerfile" \
  "${DOCKER_DIR}"

if [ -n "${GHCR_USER:-}" ] && [ -n "${GHCR_TOKEN:-}" ]; then
  echo "==> Logging in to ghcr.io..."
  echo "${GHCR_TOKEN}" | docker login "${REGISTRY}" -u "${GHCR_USER}" --password-stdin
fi

if ! docker push "${FULL_IMAGE}" 2>/dev/null; then
  echo -e "\033[1;31m✗ Push denied. Not logged in to ghcr.io.\033[0m"
  echo ""
  echo "  Opciones:"
  echo "    export GHCR_USER=<username>"
  echo "    export GHCR_TOKEN=<token>"
  echo "    docker login ghcr.io"
  exit 1
fi

echo ""
echo -e "\033[1;32m  ✓ Published ${FULL_IMAGE}\033[0m"
