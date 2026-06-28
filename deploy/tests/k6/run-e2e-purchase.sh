#!/usr/bin/env bash
set -euo pipefail

KEYCLOAK_URL="https://janrax.es/auth"
GRAPHQL_URL="https://janrax.es/graphql"
BASE_URL="https://janrax.es"
K6_USERS=50
K6_USER_PREFIX="k6user"
K6_USER_PASSWORD="test"
KEYCLOAK_ADMIN_PASSWORD="35bc14bca662a2e74654bd731a0058f3f642aa0b813617f039d730fc63736380"

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

echo "[*] Asegurando $K6_USERS usuarios de test en Keycloak..."
MASTER_TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=$KEYCLOAK_ADMIN_PASSWORD" \
  -d "grant_type=password" | jq -r '.access_token')

for i in $(seq 1 $K6_USERS); do
  USERNAME="${K6_USER_PREFIX}${i}"
  USER_ID=$(curl -s "$KEYCLOAK_URL/admin/realms/ticket-monster/users?username=$USERNAME" \
    -H "Authorization: Bearer $MASTER_TOKEN" | jq -r '.[0].id // empty')

  if [ -n "$USER_ID" ]; then
    # Update existing user
    curl -s -X PUT "$KEYCLOAK_URL/admin/realms/ticket-monster/users/$USER_ID" \
      -H "Authorization: Bearer $MASTER_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"firstName\":\"Test$i\",\"lastName\":\"User\",\"email\":\"${USERNAME}@test.com\",\"emailVerified\":true,\"enabled\":true}" \
      > /dev/null
  else
    # Create new user
    curl -s "$KEYCLOAK_URL/admin/realms/ticket-monster/users" \
      -H "Authorization: Bearer $MASTER_TOKEN" \
      -H "Content-Type: application/json" \
      -d "{\"username\":\"$USERNAME\",\"enabled\":true,\"firstName\":\"Test$i\",\"lastName\":\"User\",\"email\":\"${USERNAME}@test.com\",\"emailVerified\":true,\"credentials\":[{\"type\":\"password\",\"value\":\"$K6_USER_PASSWORD\",\"temporary\":false}]}" \
      > /dev/null
  fi
done
echo "  [✓] $K6_USERS usuarios listos"

echo "[*] Obteniendo tokens para los $K6_USERS usuarios..."
TOKENS_FILE="/tmp/k6-tokens-$$.json"
for i in $(seq 1 $K6_USERS); do
  TOKEN=$(curl -s -X POST "$KEYCLOAK_URL/realms/ticket-monster/protocol/openid-connect/token" \
    -H "Content-Type: application/x-www-form-urlencoded" \
    -d "client_id=ticket-monster-app" \
    -d "username=${K6_USER_PREFIX}${i}" \
    -d "password=$K6_USER_PASSWORD" \
    -d "grant_type=password" | jq -r '.access_token')
  echo "$TOKEN"
done | jq -R -s 'split("\n") | map(select(length > 0))' > "$TOKENS_FILE"

echo "  [✓] Tokens guardados en $TOKENS_FILE"

echo ""
echo "[*] Limpiando locks de Redis de ejecuciones anteriores..."
kubectl exec -n infrastructure redis-0 -- redis-cli KEYS "reservation:*" 2>/dev/null | while read key; do
  kubectl exec -n infrastructure redis-0 -- redis-cli DEL "$key" 2>/dev/null
done
echo "  [✓] Locks limpiados"

echo "[*] Limpiando colas de Redis de ejecuciones anteriores..."
kubectl exec -n infrastructure redis-0 -- redis-cli KEYS "queue:*" 2>/dev/null | while read key; do
  kubectl exec -n infrastructure redis-0 -- redis-cli DEL "$key" 2>/dev/null
done
echo "  [✓] Colas limpiadas"

echo "[*] Limpiando reservas y pagos de tests anteriores..."
kubectl exec -n infrastructure postgresql-0 -- psql -U ticketmonster -d ticketmonster -c "DELETE FROM reservation.reservation_items; DELETE FROM reservation.reservations; DELETE FROM payment.payment_audit; DELETE FROM payment.payments;" 2>/dev/null
echo "  [✓] Base de datos limpiada"

echo "[*] Ejecutando k6 e2e-purchase (50 VUs, 50 usuarios)..."
K6_PROMETHEUS_RW_SERVER_URL=http://localhost:9090/api/v1/write \
k6 run --out experimental-prometheus-rw \
  --duration 120s \
  --tag testid=e2e-purchase \
  e2e-purchase.js \
  -e BASE_URL="$BASE_URL" \
  -e EVENT_ID="$EVENT_ID" \
  -e TOKENS_FILE="$TOKENS_FILE"

rm -f "$TOKENS_FILE"
