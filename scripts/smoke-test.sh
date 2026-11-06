#!/usr/bin/env bash
# Smoke test del stack de Docker Compose (JP-OPS-01): comprueba por HTTP que backend, frontend (nginx) y BD
# funcionan juntos. Se ejecuta contra un stack ya levantado:
#
#   docker compose -f docker-compose.e2e.yml up -d --build --wait
#   ./scripts/smoke-test.sh
#
#   docker compose up -d --build --wait
#   DEMO_LOGIN=1 ./scripts/smoke-test.sh          # además, login con el usuario demo (solo perfil dev)
#
# Variables: FRONTEND_URL (http://localhost:5173), BACKEND_URL (http://localhost:8080), DEMO_LOGIN (0).
# JP-QA-01 sustituirá este script por la suite de Playwright en el job e2e de la CI.

set -euo pipefail

FRONTEND_URL="${FRONTEND_URL:-http://localhost:5173}"
BACKEND_URL="${BACKEND_URL:-http://localhost:8080}"
DEMO_LOGIN="${DEMO_LOGIN:-0}"

failures=0
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

pass() { echo "  ok   $1"; }
fail() { echo "  FAIL $1"; failures=$((failures + 1)); }

# request <método> <url> [cuerpo JSON] → deja el estado en $status, las cabeceras en $tmp/h y el cuerpo en $tmp/b
request() {
    local method="$1" url="$2" body="${3:-}"
    local args=(-sS -o "$tmp/b" -D "$tmp/h" -w '%{http_code}' -X "$method" --max-time 15)
    if [[ -n "$body" ]]; then
        args+=(-H 'Content-Type: application/json' --data "$body")
    fi
    : > "$tmp/b"; : > "$tmp/h"
    status="$(curl "${args[@]}" "$url" 2>/dev/null || true)"   # sin conexión, curl escribe 000
    status="${status:-000}"
}

header() { grep -i "^$1:" "$tmp/h" | head -n1 | cut -d: -f2- | tr -d '\r' | sed 's/^ *//' || true; }

expect_status() {
    if [[ "$status" == "$1" ]]; then pass "$2 → $status"; else fail "$2 → $status (se esperaba $1)"; fi
}

expect_body() {
    if grep -q -- "$1" "$tmp/b"; then pass "$2"; else fail "$2 (el cuerpo no contiene '$1')"; fi
}

expect_header_contains() {
    local value
    value="$(header "$1")"
    if [[ "$value" == *"$2"* ]]; then pass "cabecera $1 contiene $2"; else fail "cabecera $1 = '$value' (falta $2)"; fi
}

echo "Backend ($BACKEND_URL)"
request GET "$BACKEND_URL/actuator/health"
expect_status 200 "GET /actuator/health"
expect_body '"status":"UP"' "el backend está UP"

echo "Frontend ($FRONTEND_URL)"
request GET "$FRONTEND_URL/"
expect_status 200 "GET /"
expect_body 'id="root"' "sirve el index.html de la SPA"
expect_header_contains Content-Security-Policy "default-src 'self'"
expect_header_contains Content-Security-Policy "script-src 'self'"
expect_header_contains Content-Security-Policy "style-src 'self' 'unsafe-inline'"
expect_header_contains Content-Security-Policy "frame-ancestors 'none'"
expect_header_contains Content-Security-Policy "object-src 'none'"
expect_header_contains X-Content-Type-Options "nosniff"
expect_header_contains Referrer-Policy "strict-origin-when-cross-origin"
expect_header_contains X-Frame-Options "DENY"
if [[ "$(header Content-Security-Policy)" == *"script-src 'self' 'unsafe-inline'"* ]]; then
    fail "script-src no debe permitir 'unsafe-inline'"
else
    pass "script-src sin 'unsafe-inline'"
fi

request GET "$FRONTEND_URL/login"
expect_status 200 "GET /login (ruta de React Router)"
expect_body 'id="root"' "una ruta profunda devuelve el index.html"

request GET "$FRONTEND_URL/assets/no-existe.js"
expect_status 404 "GET de un asset que no existe (no se sirve el index.html)"

# La CSP (connect-src 'self') bloquea en el navegador cualquier llamada a otro origen, pero curl no aplica la CSP.
# Si la imagen se construyó con VITE_API_BASE_URL absoluto (p. ej. http://localhost:8080/api/v1), el login falla
# en el navegador aunque todo lo anterior pase. Por eso se revisa el JS generado.
echo "Bundle del frontend (VITE_API_BASE_URL relativo)"
request GET "$FRONTEND_URL/"
bundles="$(grep -oE '/assets/[^"]+\.js' "$tmp/b" | sort -u || true)"
if [[ -z "$bundles" ]]; then
    fail "el index.html no enlaza ningún /assets/*.js"
fi
for js in $bundles; do
    request GET "$FRONTEND_URL$js"
    if [[ "$status" != "200" ]]; then
        fail "GET $js → $status"
    elif grep -qE 'localhost:8080|https?://[^"'\''` ]*/api/v1' "$tmp/b"; then
        fail "$js contiene una URL absoluta de la API: $(grep -oE 'https?://[^"'\''` ]*/api/v1|localhost:8080' "$tmp/b" | head -n1) (reconstruye con VITE_API_BASE_URL=/api/v1)"
    else
        pass "$js sin URL absoluta de la API"
    fi
done

echo "Proxy de nginx (/api/v1 → backend)"
email="smoke-$(date +%s)-$RANDOM@jobpilot.test"
request POST "$FRONTEND_URL/api/v1/auth/register" \
    "{\"email\":\"$email\",\"password\":\"Smoke1234\",\"fullName\":\"Smoke Test\"}"
expect_status 201 "POST /api/v1/auth/register a través de nginx"
expect_body '"accessToken"' "devuelve un token"

request GET "$FRONTEND_URL/api/v1/profile"
expect_status 401 "GET /api/v1/profile sin token → 401 del backend"

if [[ "$DEMO_LOGIN" == "1" ]]; then
    echo "Usuario demo (perfil dev)"
    request POST "$FRONTEND_URL/api/v1/auth/login" '{"email":"demo@jobpilot.dev","password":"Demo1234!"}'
    expect_status 200 "POST /api/v1/auth/login con demo@jobpilot.dev"
fi

echo
if [[ "$failures" -gt 0 ]]; then
    echo "Smoke test: $failures comprobación(es) fallida(s)."
    exit 1
fi
echo "Smoke test: todo correcto."
