#!/usr/bin/env bash
set -euo pipefail

# ============================================================
# Ticket Monster — Frontend CLI Emulator
# ============================================================
# Uso: ./frontend.sh <usuario> <password> [base_url] [-v]
#   base_url: URL del monólito (default: https://janrax.es)
#   -v: modo verbose (muestra comandos curl)
# ============================================================

# --- URLs por defecto ---
DEFAULT_BASE="https://janrax.es"
DEFAULT_KEYCLOAK="https://janrax.es/auth"

# --- Estado interno ---
ACCESS_TOKEN=""
REFRESH_TOKEN=""
EXPIRES_AT=0
ROLE=""
JQ_AVAILABLE=false
API_BASE_URL=""
KEYCLOAK_URL=""
VERBOSE=false
CLIENT_ID="ticket-monster-app"

# === FUNCIONES AUXILIARES ===

check_deps() {
    if command -v jq &>/dev/null; then
        JQ_AVAILABLE=true
    else
        echo "[!] jq no está instalado. Las respuestas JSON se mostrarán sin formato."
        echo "    Instálalo con: sudo apt install jq  (o brew install jq)"
        echo ""
    fi
}

mostrar_json() {
    if $JQ_AVAILABLE; then
        echo "$1" | jq .
    else
        echo "$1"
    fi
}

leer_input() {
    local prompt="$1"
    local valor=""
    while true; do
        read -r -p "$prompt" valor
        if [[ -n "$valor" ]]; then
            echo "$valor"
            return
        fi
        echo "  [!] El valor no puede estar vacío."
    done
}

# === AUTENTICACIÓN ===

health_check() {
    echo "[*] Verificando entorno..."

    if curl -s -o /dev/null --connect-timeout 5 "$API_BASE_URL/graphql" 2>/dev/null; then
        echo "  [✓] Monólito ($API_BASE_URL)"
    else
        echo "[ERROR] Monólito no responde en $API_BASE_URL"
        exit 1
    fi

    if curl -sf "$KEYCLOAK_URL" >/dev/null 2>&1; then
        echo "  [✓] Keycloak ($KEYCLOAK_URL)"
    else
        echo "[!] No se pudo verificar Keycloak en $KEYCLOAK_URL"
        echo "    El login puede fallar si la URL es incorrecta."
    fi
}

login() {
    local username="$1" password="$2"
    echo "[*] Autenticando como '$username'..."

    local response
    response=$(curl -s -X POST "$KEYCLOAK_URL/realms/ticket-monster/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "client_id=$CLIENT_ID" \
        -d "username=$username" \
        -d "password=$password" \
        -d "grant_type=password")

    if echo "$response" | jq -e '.access_token' >/dev/null 2>&1; then
        ACCESS_TOKEN=$(echo "$response" | jq -r '.access_token')
        REFRESH_TOKEN=$(echo "$response" | jq -r '.refresh_token')
        local expires_in
        expires_in=$(echo "$response" | jq -r '.expires_in')
        EXPIRES_AT=$(( $(date +%s) + expires_in ))
        echo "  [✓] Token obtenido (expira en ${expires_in}s)"
    else
        local error_desc
        error_desc=$(echo "$response" | jq -r '.error_description // .error // "Error desconocido"')
        echo "[ERROR] Autenticación fallida: $error_desc"
        exit 1
    fi
}

refresh_token() {
    echo "[*] Renovando token..."
    local response
    response=$(curl -s -X POST "$KEYCLOAK_URL/realms/ticket-monster/protocol/openid-connect/token" \
        -H "Content-Type: application/x-www-form-urlencoded" \
        -d "client_id=$CLIENT_ID" \
        -d "refresh_token=$REFRESH_TOKEN" \
        -d "grant_type=refresh_token")

    if echo "$response" | jq -e '.access_token' >/dev/null 2>&1; then
        ACCESS_TOKEN=$(echo "$response" | jq -r '.access_token')
        REFRESH_TOKEN=$(echo "$response" | jq -r '.refresh_token')
        local expires_in
        expires_in=$(echo "$response" | jq -r '.expires_in')
        EXPIRES_AT=$(( $(date +%s) + expires_in ))
        echo "  [✓] Token renovado"
    else
        echo "[ERROR] Sesión expirada. Vuelve a ejecutar el script."
        exit 1
    fi
}

ensure_token() {
    local now
    now=$(date +%s)
    if (( now >= EXPIRES_AT - 30 )); then
        refresh_token
    fi
}

# === LLAMADAS API ===

api_call() {
    local method="$1" url="$2" data="${3:-}" content_type="${4:-application/json}"
    ensure_token

    if $VERBOSE; then
        echo ""
        echo "  ▶ curl -s -X $method \\"
        echo "      '$url' \\"
        echo "      -H 'Authorization: Bearer $ACCESS_TOKEN' \\"
        if [[ "$method" != "GET" ]]; then
            echo "      -H 'Content-Type: $content_type' \\"
            echo "      -d '$data'"
        fi
        echo ""
    fi >&2

    local response http_code
    if [[ "$method" == "GET" ]]; then
        response=$(curl -s -w "\n%{http_code}" "$url" \
            -H "Authorization: Bearer $ACCESS_TOKEN")
    else
        response=$(curl -s -w "\n%{http_code}" "$url" \
            -H "Authorization: Bearer $ACCESS_TOKEN" \
            -H "Content-Type: $content_type" \
            -d "$data")
    fi

    http_code=$(echo "$response" | tail -1)
    response=$(echo "$response" | sed '$d')

    if [[ "$http_code" == "401" ]]; then
        refresh_token
        if [[ "$method" == "GET" ]]; then
            response=$(curl -s -w "\n%{http_code}" "$url" \
                -H "Authorization: Bearer $ACCESS_TOKEN")
        else
            response=$(curl -s -w "\n%{http_code}" "$url" \
                -H "Authorization: Bearer $ACCESS_TOKEN" \
                -H "Content-Type: $content_type" \
                -d "$data")
        fi
        http_code=$(echo "$response" | tail -1)
        response=$(echo "$response" | sed '$d')
    fi

    echo "HTTP:$http_code"
    echo "$response"
}

graphql() {
    local query="$1"
    api_call "POST" "$API_BASE_URL/graphql" "{\"query\": $(echo "$query" | jq -Rs .)}"
}

rest_call() {
    local method="$1" path="$2" data="${3:-}"
    api_call "$method" "$API_BASE_URL$path" "$data"
}

api_extract_http() {
    echo "$1" | head -1 | sed 's/HTTP://'
}

api_extract_body() {
    echo "$1" | sed '1d'
}

# === DECODIFICACIÓN JWT ===

detect_role() {
    local payload
    payload=$(echo "$ACCESS_TOKEN" | cut -d'.' -f2)
    local decoded
    decoded=$(echo "$payload" | base64 -d 2>/dev/null || echo "$payload" | base64 -d - 2>/dev/null)

    if echo "$decoded" | jq -e '.realm_access.roles' >/dev/null 2>&1; then
        local roles
        roles=$(echo "$decoded" | jq -r '.realm_access.roles[]')
        if echo "$roles" | grep -q "ADMIN"; then
            ROLE="ADMIN"
        else
            ROLE="USER"
        fi
    else
        ROLE="USER"
    fi
    echo "  [✓] Rol detectado: $ROLE"
}

# === ACCIONES DE ADMINISTRADOR ===

crear_artista() {
    echo ""
    echo "--- Crear Artista ---"
    local name genre
    name=$(leer_input "  Nombre del artista: ")
    genre=$(leer_input "  Género: ")

    local q
    q=$(cat <<EOF
mutation {
  createArtist(input: { name: "$name", genre: "$genre" }) {
    id name
  }
}
EOF
)
    local result
    result=$(graphql "$q")
    local http_code=$(api_extract_http "$result")
    local body=$(api_extract_body "$result")

    if [[ "$http_code" == "2"* ]]; then
        echo ""
        echo "  [✓] Artista creado:"
        mostrar_json "$body"
        local artist_id
        artist_id=$(echo "$body" | jq -r '.data.createArtist.id // empty')
        if [[ -n "$artist_id" ]]; then
            echo ""
            echo "  >>> ID del Artista: $artist_id <<<"
        fi
    else
        echo "[ERROR] Fallo al crear artista:"
        mostrar_json "$body"
    fi
}

crear_venue() {
    echo ""
    echo "--- Crear Venue ---"
    local name capacity
    name=$(leer_input "  Nombre del venue: ")
    capacity=$(leer_input "  Capacidad total: ")

    local q
    q=$(cat <<EOF
mutation {
  createVenue(input: { name: "$name", totalCapacity: $capacity }) {
    id name totalCapacity
  }
}
EOF
)
    local result
    result=$(graphql "$q")
    local http_code=$(api_extract_http "$result")
    local body=$(api_extract_body "$result")

    if [[ "$http_code" == "2"* ]]; then
        echo ""
        echo "  [✓] Venue creado:"
        mostrar_json "$body"
        local venue_id
        venue_id=$(echo "$body" | jq -r '.data.createVenue.id // empty')
        if [[ -n "$venue_id" ]]; then
            echo ""
            echo "  >>> ID del Venue: $venue_id <<<"
        fi
    else
        echo "[ERROR] Fallo al crear venue:"
        mostrar_json "$body"
    fi
}

crear_evento() {
    echo ""
    echo "--- Crear Evento ---"
    local name type date venue_id
    name=$(leer_input "  Nombre del evento: ")
    type=$(leer_input "  Tipo (CONCERT|SPORTS|THEATER|FESTIVAL|CONFERENCE|OTHER): ")
    date=$(leer_input "  Fecha (YYYY-MM-DDTHH:MM:SS): ")
    venue_id=$(leer_input "  ID del Venue: ")

    echo ""
    echo "  Zonas:"
    local zones_graphql="["
    local first=true
    while true; do
        echo ""
        local zone_name zone_cap zone_price
        zone_name=$(leer_input "    Nombre de la zona: ")
        zone_cap=$(leer_input "    Capacidad: ")
        zone_price=$(leer_input "    Precio: ")

        if $first; then
            first=false
        else
            zones_graphql+=", "
        fi
        zones_graphql+="{name: \"$zone_name\", capacity: $zone_cap, price: $zone_price}"

        local add_more
        read -r -p "    ¿Añadir otra zona? (s/N): " add_more
        if [[ "$add_more" != "s" && "$add_more" != "S" ]]; then
            break
        fi
    done
    zones_graphql+="]"

    local q
    q="mutation { createEvent(input: { name: \"$name\", type: $type, date: \"$date\", venueId: \"$venue_id\", zones: $zones_graphql }) { id name status zones { id name capacity price } } }"
    local result
    result=$(graphql "$q")
    local http_code=$(api_extract_http "$result")
    local body=$(api_extract_body "$result")

    if [[ "$http_code" == "2"* ]]; then
        echo ""
        echo "  [✓] Evento creado:"
        mostrar_json "$body"
        local event_id
        event_id=$(echo "$body" | jq -r '.data.createEvent.id // empty')
        if [[ -n "$event_id" ]]; then
            echo ""
            echo "  >>> ID del Evento: $event_id <<<"
            local zones
            zones=$(echo "$body" | jq -r '.data.createEvent.zones[] | "  >>> Zona: \(.name) → ID: \(.id) <<<"' 2>/dev/null)
            if [[ -n "$zones" ]]; then
                echo "$zones"
            fi
        fi
    else
        echo "[ERROR] Fallo al crear evento:"
        mostrar_json "$body"
    fi
}

publicar_evento() {
    echo ""
    echo "--- Publicar Evento ---"
    local event_id
    event_id=$(leer_input "  ID del Evento: ")

    local q
    q=$(cat <<EOF
mutation {
  updateEvent(id: "$event_id", input: { status: PUBLISHED }) {
    id name status
  }
}
EOF
)
    local result
    result=$(graphql "$q")
    local http_code=$(api_extract_http "$result")
    local body=$(api_extract_body "$result")

    if [[ "$http_code" == "2"* ]]; then
        echo ""
        echo "  [✓] Evento publicado:"
        mostrar_json "$body"
    else
        echo "[ERROR] Fallo al publicar evento:"
        mostrar_json "$body"
    fi
}

listar_eventos() {
    echo ""
    echo "--- Listar Eventos ---"
    local q='{ events(page: 0, size: 20) { content { id name venue { name } status zones { id name capacity price } } } }'
    local result
    result=$(graphql "$q")
    local http_code=$(api_extract_http "$result")
    local body=$(api_extract_body "$result")

    if [[ "$http_code" == "2"* ]]; then
        echo ""
        mostrar_json "$body"
    else
        echo "[ERROR] Fallo al listar eventos:"
        mostrar_json "$body"
    fi
}

ver_disponibilidad() {
    echo ""
    echo "--- Ver Disponibilidad ---"
    local event_id
    event_id=$(leer_input "  ID del Evento: ")

    local q
    q=$(cat <<EOF
{ availability(eventId: "$event_id") { zoneId zoneName totalCapacity reservedCount availableCount } }
EOF
)
    local result
    result=$(graphql "$q")
    local http_code=$(api_extract_http "$result")
    local body=$(api_extract_body "$result")

    if [[ "$http_code" == "2"* ]]; then
        echo ""
        mostrar_json "$body"
    else
        echo "[ERROR] Fallo al consultar disponibilidad:"
        mostrar_json "$body"
    fi
}

# === ACCIONES DE USUARIO ===

listar_eventos_user() {
    listar_eventos
}

ver_disponibilidad_user() {
    ver_disponibilidad
}

comprar_entradas() {
    echo ""
    echo "--- Comprar Entradas ---"
    local event_id
    event_id=$(leer_input "  ID del Evento: ")

    # 1. Join queue
    echo ""
    echo "[*] Uniéndose a la cola virtual..."
    local join_result
    join_result=$(rest_call "POST" "/api/v1/queue/$event_id/join")
    local join_http=$(api_extract_http "$join_result")
    local join_body=$(api_extract_body "$join_result")

    if [[ "$join_http" != "2"* ]]; then
        echo "[ERROR] Fallo al unirse a la cola:"
        mostrar_json "$join_body"
        return
    fi

    local ticket_id position
    ticket_id=$(echo "$join_body" | jq -r '.ticketId // empty')
    position=$(echo "$join_body" | jq -r '.position // empty')
    echo "  [✓] En cola — Ticket: $ticket_id, Posición: $position"

    # 2. Polling hasta TURN_READY
    echo ""
    echo "[*] Esperando turno..."
    local attempts=0
    local max_attempts=60
    while true; do
        sleep 2
        local status_result
        status_result=$(rest_call "GET" "/api/v1/queue/$event_id/status")
        local status_http=$(api_extract_http "$status_result")
        local status_body=$(api_extract_body "$status_result")

        if [[ "$status_http" != "2"* ]]; then
            echo "[ERROR] Fallo al consultar estado de cola:"
            mostrar_json "$status_body"
            return
        fi

        local queue_status
        queue_status=$(echo "$status_body" | jq -r '.status // "WAITING"')
        local current_pos
        current_pos=$(echo "$status_body" | jq -r '.position // "?"')

        if [[ "$queue_status" == "TURN_READY" ]]; then
            echo "  [✓] ¡Turno asignado!"
            break
        fi

        attempts=$((attempts + 1))
        echo -ne "  Esperando turno... posición: $current_pos  \r"

        if (( attempts >= max_attempts )); then
            echo ""
            local seguir
            read -r -p "  ¿Seguir esperando? (s/N): " seguir
            if [[ "$seguir" != "s" && "$seguir" != "S" ]]; then
                echo "[!] Operación cancelada."
                return
            fi
            attempts=0
        fi
    done

    # 3. Obtener token de acceso
    echo ""
    echo "[*] Obteniendo token de acceso..."
    local token_result
    token_result=$(rest_call "GET" "/api/v1/queue/$event_id/token")
    local token_http=$(api_extract_http "$token_result")
    local token_body=$(api_extract_body "$token_result")

    if [[ "$token_http" != "2"* ]]; then
        echo "[ERROR] Fallo al obtener token:"
        mostrar_json "$token_body"
        return
    fi
    echo "  [✓] Token obtenido"

    # 4. Crear reserva
    echo ""
    local zone_id quantity
    zone_id=$(leer_input "  ID de la Zona: ")
    quantity=$(leer_input "  Cantidad de entradas: ")

    local reservation_data
    reservation_data=$(cat <<EOF
{"eventId":"$event_id","items":[{"zoneId":"$zone_id","quantity":$quantity}]}
EOF
)
    local res_result
    res_result=$(rest_call "POST" "/api/v1/reservations" "$reservation_data")
    local res_http=$(api_extract_http "$res_result")
    local res_body=$(api_extract_body "$res_result")

    if [[ "$res_http" == "2"* ]]; then
        echo ""
        echo "  [✓] Reserva creada:"
        mostrar_json "$res_body"
        local reservation_id
        reservation_id=$(echo "$res_body" | jq -r '.id // empty')
        if [[ -n "$reservation_id" ]]; then
            echo ""
            echo "  >>> ID de Reserva: $reservation_id <<<"
        fi
    else
        echo "[ERROR] Fallo al crear reserva:"
        mostrar_json "$res_body"
    fi
}

pagar_reserva() {
    echo ""
    echo "--- Pagar Reserva ---"
    local reservation_id
    reservation_id=$(leer_input "  ID de la Reserva: ")

    # 1. Consultar reserva
    echo ""
    echo "[*] Consultando reserva..."
    local res_result
    res_result=$(rest_call "GET" "/api/v1/reservations/$reservation_id")
    local res_http=$(api_extract_http "$res_result")
    local res_body=$(api_extract_body "$res_result")

    if [[ "$res_http" != "2"* ]]; then
        echo "[ERROR] No se pudo obtener la reserva:"
        mostrar_json "$res_body"
        return
    fi

    # 2. Verificar estado
    local res_status
    res_status=$(echo "$res_body" | jq -r '.status // "UNKNOWN"')
    if [[ "$res_status" != "ACTIVE" ]]; then
        echo ""
        echo "  [!] La reserva no está activa. Estado: $res_status"
        if [[ "$res_status" == "SOLD" ]]; then
            echo "  [!] Esta reserva ya ha sido pagada."
        fi
        mostrar_json "$res_body"
        return
    fi

    echo "  [✓] Reserva activa"
    echo ""
    mostrar_json "$res_body"

    # 3. Calcular total esperado desde el evento
    local event_id
    event_id=$(echo "$res_body" | jq -r '.eventId')
    local items_json
    items_json=$(echo "$res_body" | jq -c '.items')

    echo ""
    echo "[*] Calculando total esperado..."
    local q
    q=$(cat <<EOF
{ event(id: "$event_id") { zones { id name price } } }
EOF
)
    local event_result
    event_result=$(graphql "$q")
    local event_http=$(api_extract_http "$event_result")
    local event_body=$(api_extract_body "$event_result")

    local expected_total=0
    if [[ "$event_http" == "2"* ]]; then
        expected_total=$(echo "$event_body" | jq -r --argjson items "$items_json" '
          [.data.event.zones[] as $zone
           | ($items[] | select(.zoneId == $zone.id) | .quantity * $zone.price)
          ] | add // 0
        ')
        echo ""
        echo "  Artículos:"
        echo "$items_json" | jq -r '.[] | "    \(.quantity) x entrada(s) (zona: \(.zoneId))"'
        echo ""
        echo "  >>> Precio total esperado: $expected_total € <<<"
    else
        echo "  [!] No se pudieron obtener los precios del evento."
    fi

    # 4. Pedir monto
    echo ""
    local amount
    read -r -p "  Monto a pagar [$expected_total]: " amount
    if [[ -z "$amount" ]]; then
        amount=$expected_total
    fi

    if [[ "$amount" != "$expected_total" ]]; then
        echo "  [!] Atención: el monto ingresado ($amount) difiere del esperado ($expected_total)."
        local confirmar
        read -r -p "  ¿Continuar de todas formas? (s/N): " confirmar
        if [[ "$confirmar" != "s" && "$confirmar" != "S" ]]; then
            echo "[!] Pago cancelado."
            return
        fi
    fi

    # 5. Crear pago
    local payment_data
    payment_data=$(cat <<EOF
{"reservationId":"$reservation_id","amount":$amount}
EOF
)
    echo ""
    echo "[*] Creando pago..."
    local pay_result
    pay_result=$(rest_call "POST" "/api/v1/payments" "$payment_data")
    local pay_http=$(api_extract_http "$pay_result")
    local pay_body=$(api_extract_body "$pay_result")

    if [[ "$pay_http" != "2"* ]]; then
        echo "[ERROR] Fallo al crear pago:"
        mostrar_json "$pay_body"
        return
    fi

    echo ""
    mostrar_json "$pay_body"
    local payment_id
    payment_id=$(echo "$pay_body" | jq -r '.id // empty')

    if [[ -z "$payment_id" ]]; then
        echo "[ERROR] No se pudo obtener el ID del pago."
        return
    fi
    echo ""
    echo "  >>> ID del Pago: $payment_id <<<"

    # 6. Confirmar pago
    local idempotency_key
    idempotency_key="cli-$(date +%s)-$$"
    local confirm_data
    confirm_data=$(cat <<EOF
{"idempotencyKey":"$idempotency_key"}
EOF
)
    echo ""
    echo "[*] Confirmando pago..."
    local conf_result
    conf_result=$(rest_call "POST" "/api/v1/payments/$payment_id/confirm" "$confirm_data")
    local conf_http=$(api_extract_http "$conf_result")
    local conf_body=$(api_extract_body "$conf_result")

    if [[ "$conf_http" == "2"* ]]; then
        echo ""
        echo "  [✓] Pago confirmado:"
        mostrar_json "$conf_body"
    else
        echo "[ERROR] Fallo al confirmar pago:"
        mostrar_json "$conf_body"
    fi
}

ver_reserva() {
    echo ""
    echo "--- Ver Reserva ---"
    local reservation_id
    reservation_id=$(leer_input "  ID de la Reserva: ")

    local result
    result=$(rest_call "GET" "/api/v1/reservations/$reservation_id")
    local http_code=$(api_extract_http "$result")
    local body=$(api_extract_body "$result")

    if [[ "$http_code" == "2"* ]]; then
        echo ""
        mostrar_json "$body"
    else
        echo "[ERROR] Fallo al consultar reserva:"
        mostrar_json "$body"
    fi
}

# === MENUS ===

menu_admin() {
    while true; do
        echo ""
        echo "╔══════════════════════════════════╗"
        echo "║      MENÚ ADMINISTRADOR          ║"
        echo "╠══════════════════════════════════╣"
        echo "║ 1. Crear Artista                 ║"
        echo "║ 2. Crear Venue                   ║"
        echo "║ 3. Crear Evento                  ║"
        echo "║ 4. Publicar Evento               ║"
        echo "║ 5. Listar Eventos                ║"
        echo "║ 6. Ver disponibilidad            ║"
        echo "║ 7. Salir                         ║"
        echo "╚══════════════════════════════════╝"
        echo ""
        local opcion
        read -r -p "  Opción: " opcion
        echo ""

        case "$opcion" in
            1) crear_artista ;;
            2) crear_venue ;;
            3) crear_evento ;;
            4) publicar_evento ;;
            5) listar_eventos ;;
            6) ver_disponibilidad ;;
            7)
                echo "[*] Hasta luego."
                exit 0
                ;;
            *) echo "[!] Opción inválida. Intenta de nuevo." ;;
        esac
    done
}

menu_user() {
    while true; do
        echo ""
        echo "╔══════════════════════════════════╗"
        echo "║      MENÚ USUARIO                ║"
        echo "╠══════════════════════════════════╣"
        echo "║ 1. Listar Eventos                ║"
        echo "║ 2. Ver disponibilidad            ║"
        echo "║ 3. Comprar entradas              ║"
        echo "║ 4. Pagar reserva                 ║"
        echo "║ 5. Ver reserva                   ║"
        echo "║ 6. Salir                         ║"
        echo "╚══════════════════════════════════╝"
        echo ""
        local opcion
        read -r -p "  Opción: " opcion
        echo ""

        case "$opcion" in
            1) listar_eventos_user ;;
            2) ver_disponibilidad_user ;;
            3) comprar_entradas ;;
            4) pagar_reserva ;;
            5) ver_reserva ;;
            6)
                echo "[*] Hasta luego."
                exit 0
                ;;
            *) echo "[!] Opción inválida. Intenta de nuevo." ;;
        esac
    done
}

# === PUNTO DE ENTRADA PRINCIPAL ===

main() {
    echo ""
    echo "============================================"
    echo "  Ticket Monster — Frontend CLI Emulator"
    echo "============================================"
    echo ""

    # Parsear argumentos
    local username="" password=""
    local args=()

    for arg in "$@"; do
        if [[ "$arg" == "-v" ]]; then
            VERBOSE=true
        else
            args+=("$arg")
        fi
    done

    if [[ ${#args[@]} -lt 2 ]]; then
        echo "Uso: $0 <usuario> <password> [base_url] [-v]"
        echo ""
        echo "Usuarios de prueba:"
        echo "  admin / admin  (roles: ADMIN, USER)"
        echo "  user  / user   (rol: USER)"
        echo ""
        echo "Argumentos:"
        echo "  base_url      URL del monólito (default: $DEFAULT_BASE)"
        echo "  keycloak_url  URL de Keycloak (default: $DEFAULT_KEYCLOAK)"
        echo "  -v            Muestra el comando curl equivalente"
        echo ""
        echo "Ejemplos:"
        echo "  $0 admin admin"
        echo "  $0 user user https://ticketmonster.example.com"
        echo "  $0 admin admin http://localhost:8082 http://localhost:8180"
        echo "  $0 admin admin -v"
        echo ""
        exit 1
    fi

    username="${args[0]}"
    password="${args[1]}"
    API_BASE_URL="${args[2]:-$DEFAULT_BASE}"
    KEYCLOAK_URL="${args[3]:-$DEFAULT_KEYCLOAK}"

    if $VERBOSE; then
        echo "  [*] Modo verbose activado"
    fi

    check_deps
    health_check
    login "$username" "$password"
    detect_role

    echo ""
    echo "  Bienvenido, $username ($ROLE)"
    echo "  API: $API_BASE_URL"
    echo ""

    if [[ "$ROLE" == "ADMIN" ]]; then
        menu_admin
    else
        menu_user
    fi
}

main "$@"
