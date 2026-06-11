#!/usr/bin/env bash
# Diagnose 403 on /api/v1/admin/zoho/* (gateway → integration :9183).
# Usage on VPS:
#   UAT_ADMIN_EMAIL=admin@uat.minisnmore.com UAT_ADMIN_PASSWORD=admin6 \
#   bash deploy/scripts/uat-diagnose-zoho-admin.sh
set -euo pipefail

GATEWAY_PORT="${GATEWAY_PORT:-9180}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"
ADMIN_EMAIL="${UAT_ADMIN_EMAIL:-admin@uat.minisnmore.com}"
ADMIN_PASSWORD="${UAT_ADMIN_PASSWORD:-admin6}"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

echo "=== PM2 integration ==="
pm2 describe mini-uat-ms-integration 2>/dev/null | grep -E 'status|port|JWT|auth' || pm2 list | grep integration || echo "WARN: mini-uat-ms-integration not in pm2"

echo ""
echo "=== Liveness ==="
for label_port in "integration:${INTEGRATION_PORT}" "gateway:${GATEWAY_PORT}"; do
  label="${label_port%%:*}"
  port="${label_port##*:}"
  code=$(curl -sS -m 5 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${port}/management/health/liveness" || echo ERR)
  echo "${label} :${port} → ${code}"
done

echo ""
echo "=== Admin JWT ==="
AUTH_BODY=$(printf '{"username":"%s","password":"%s"}' "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
TOKEN=$(curl -sS -m 15 -X POST "http://127.0.0.1:${GATEWAY_PORT}/api/authenticate" \
  -H "Content-Type: application/json" \
  -d "$AUTH_BODY" | python3 -c "import sys,json; print(json.load(sys.stdin)['id_token'], end='')")
if [ -z "$TOKEN" ]; then
  echo "FAIL could not obtain id_token for ${ADMIN_EMAIL}" >&2
  exit 1
fi
echo "OK  id_token length ${#TOKEN}"
echo "JWT auth claim (roles):"
echo "$TOKEN" | python3 -c "
import sys, json, base64
t = sys.stdin.read().strip().split('.')[1]
t += '=' * (-len(t) % 4)
print(json.loads(base64.urlsafe_b64decode(t)).get('auth', '<missing>'))
" 2>/dev/null || echo "(could not decode)"

check() {
  local label="$1"
  local method="$2"
  local url="$3"
  local code body
  body=$(curl -sS -m 45 -X "$method" -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
    -d '{}' "http://127.0.0.1:${url}" 2>/dev/null || true)
  code=$(curl -sS -m 45 -o /dev/null -w "%{http_code}" -X "$method" -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" -d '{}' "http://127.0.0.1:${url}" 2>/dev/null || echo ERR)
  echo "${label} → ${code}"
  if [ "$code" != "200" ] && [ -n "$body" ]; then
    echo "  body: $(echo "$body" | head -c 200)"
  fi
}

echo ""
echo "=== No auth (expect 401 on protected routes) ==="
NOAUTH=$(curl -sS -m 10 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${INTEGRATION_PORT}/api/v1/admin/zoho/integration-status" || echo ERR)
echo "integration GET integration-status without token → ${NOAUTH}"

check_post_no_body() {
  local label="$1"
  local url="$2"
  local code
  code=$(curl -sS -m 45 -o /dev/null -w "%{http_code}" -X POST -H "Authorization: Bearer ${TOKEN}" \
    "http://127.0.0.1:${url}" 2>/dev/null || echo ERR)
  echo "${label} → ${code}"
}

echo ""
echo "=== Zoho admin routes (expect 200 for admin JWT) ==="
check "integration GET  /admin/zoho/integration-status" GET "${INTEGRATION_PORT}/api/v1/admin/zoho/integration-status"
check "integration GET  /admin/zoho/product-sync-status" GET "${INTEGRATION_PORT}/api/v1/admin/zoho/product-sync-status"
check "integration GET  /admin/zoho-settings" GET "${INTEGRATION_PORT}/api/v1/admin/zoho-settings"
check "integration GET  /admin/zoho/test" GET "${INTEGRATION_PORT}/api/v1/admin/zoho/test"
check "integration POST /admin/zoho/sync-products" POST "${INTEGRATION_PORT}/api/v1/admin/zoho/sync-products"
check "integration POST /admin/zoho/retry-orders" POST "${INTEGRATION_PORT}/api/v1/admin/zoho/retry-orders"
check_post_no_body "integration POST /admin/zoho/retry-orders (no body)" "${INTEGRATION_PORT}/api/v1/admin/zoho/retry-orders"
check "gateway     GET  /admin/zoho/integration-status" GET "${GATEWAY_PORT}/api/v1/admin/zoho/integration-status"
check "gateway     GET  /admin/zoho/product-sync-status" GET "${GATEWAY_PORT}/api/v1/admin/zoho/product-sync-status"
check "gateway     GET  /admin/zoho-settings" GET "${GATEWAY_PORT}/api/v1/admin/zoho-settings"
check "gateway     POST /admin/zoho/retry-orders" POST "${GATEWAY_PORT}/api/v1/admin/zoho/retry-orders"

echo ""
echo "=== Process / jar ==="
ss -tlnp "sport = :${INTEGRATION_PORT}" 2>/dev/null || true
PM2_PID=$(pm2 jlist 2>/dev/null | python3 -c "
import json, sys
for p in json.load(sys.stdin):
    if p.get('name') == 'mini-uat-ms-integration':
        print(p.get('pid', ''))
        break
" 2>/dev/null || true)
echo "pm2 pid=${PM2_PID:-?}"
ls -la /var/www/uat1/mini_ms_test/integration/target/miniandmore-integration-*.jar 2>/dev/null || true

echo ""
echo "If integration=403 but JWT auth claim contains ROLE_ADMIN, restart integration with JWT env:"
echo "  REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-restart-isolated-integration.sh"
echo "If integration liveness fails, start the isolated stack or restart integration only."
