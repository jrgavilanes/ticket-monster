#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL="https://janrax.es/auth"
GRAPHQL_URL="https://janrax.es/graphql"
BASE_URL="https://janrax.es"
K6_VUS=200
K6_DURATION="30s"

echo "[*] Obteniendo token de admin (admin/admin)..."
ADMIN_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/ticket-monster/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=ticket-monster-app" \
  -d "username=admin" -d "password=admin" \
  -d "grant_type=password" | jq -r '.access_token')

if [ -z "$ADMIN_TOKEN" ] || [ "$ADMIN_TOKEN" = "null" ]; then
  echo "[ERROR] No se pudo obtener el token de admin"
  exit 1
fi
echo "  [✓] Admin token obtenido"

echo "[*] Creando venue..."
VENUE_ID=$(curl -s "$GRAPHQL_URL" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"query":"mutation { createVenue(input: { name: \"Estadio Test K6\", city: \"Madrid\", country: \"ES\", totalCapacity: 500 }) { id } }"}' \
  | jq -r '.data.createVenue.id')

echo "  [✓] Venue ID=$VENUE_ID"

echo "[*] Creando artist..."
ARTIST_ID=$(curl -s "$GRAPHQL_URL" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d '{"query":"mutation { createArtist(input: { name: \"DJ Test K6\", genre: \"Electronic\" }) { id } }"}' \
  | jq -r '.data.createArtist.id')

echo "  [✓] Artist ID=$ARTIST_ID"

FUTURE_DATE=$(date -u -d "+7 days" +"%Y-%m-%dT20:00:00")

echo "[*] Creando evento con zonas (zone-general: 5000, zone-vip: 1000)..."
EVENT_ID=$(curl -s "$GRAPHQL_URL" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"query\":\"mutation { createEvent(input: { name: \\\"Concierto Test K6\\\", type: CONCERT, date: \\\"$FUTURE_DATE\\\", venueId: \\\"$VENUE_ID\\\", artistIds: [\\\"$ARTIST_ID\\\"], zones: [{ id: \\\"zone-general\\\", name: \\\"General\\\", capacity: 5000, price: 50.0 }, { id: \\\"zone-vip\\\", name: \\\"VIP\\\", capacity: 1000, price: 150.0 }] }) { id zones { id name capacity } } }\"}" \
  | jq -r '.data.createEvent.id')

echo "  [✓] Event ID=$EVENT_ID"

echo "[*] Publicando evento..."
curl -s "$GRAPHQL_URL" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $ADMIN_TOKEN" \
  -d "{\"query\":\"mutation { updateEvent(id: \\\"$EVENT_ID\\\", input: { status: PUBLISHED }) { id status } }\"}" \
  > /dev/null

echo "  [✓] Evento publicado"

echo ""
echo "[*] Ejecutando k6 catalog-read ($K6_VUS VUs, $K6_DURATION duración, endpoint público sin auth)..."
k6 run \
  --vus $K6_VUS \
  --duration $K6_DURATION \
  catalog-read.js \
  -e BASE_URL="$BASE_URL" \
  -e EVENT_ID="$EVENT_ID"
