#!/usr/bin/env bash
# Quick health report for isolated UAT stack (run on VPS).
# Usage: bash deploy/scripts/uat-verify-isolated-stack.sh
set -euo pipefail

GATEWAY_PORT="${GATEWAY_PORT:-9180}"
STORE_PORT="${STORE_PORT:-9181}"
COMMERCE_PORT="${COMMERCE_PORT:-9182}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
FAIL=0

check_port() {
  local port="$1"
  local label="$2"
  if ss -tlnp 2>/dev/null | grep -q ":${port} "; then
    echo "OK   port :${port} (${label}) listening"
  else
    echo "FAIL port :${port} (${label}) not listening" >&2
    FAIL=1
  fi
}

check_http() {
  local url="$1"
  local label="$2"
  local code
  code=$(curl -sS -m 8 -o /dev/null -w "%{http_code}" "$url" 2>/dev/null || echo ERR)
  if [ "$code" = "200" ] || [ "$code" = "401" ] || [ "$code" = "403" ]; then
    echo "OK   ${label} (${code}) ${url}"
  else
    echo "FAIL ${label} (${code}) ${url}" >&2
    FAIL=1
  fi
}

echo "=== PM2 ==="
pm2 list 2>/dev/null | grep -E 'mini-uat|name|online|errored|stopped' || pm2 list

echo ""
echo "=== Ports ==="
check_port "$GATEWAY_PORT" "gateway"
check_port "$STORE_PORT" "store"
check_port "$COMMERCE_PORT" "commerce"
check_port "$INTEGRATION_PORT" "integration"
check_port "$CATALOG_PORT" "catalog"
check_port "$FRONTEND_PORT" "frontend"

echo ""
echo "=== Liveness (localhost) ==="
check_http "http://127.0.0.1:${CATALOG_PORT}/management/health/liveness" "catalog"
check_http "http://127.0.0.1:${STORE_PORT}/management/health/liveness" "store"
check_http "http://127.0.0.1:${COMMERCE_PORT}/management/health/liveness" "commerce"
check_http "http://127.0.0.1:${INTEGRATION_PORT}/management/health/liveness" "integration"
check_http "http://127.0.0.1:${GATEWAY_PORT}/management/health/liveness" "gateway"

echo ""
echo "=== API path (gateway → store → catalog) ==="
code=$(curl -sS -m 15 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${GATEWAY_PORT}/api/v1/categories" 2>/dev/null || echo ERR)
if [ "$code" = "200" ]; then
  echo "OK   GET /api/v1/categories via gateway (${code})"
else
  echo "FAIL GET /api/v1/categories via gateway (${code})" >&2
  FAIL=1
fi

echo ""
echo "=== API path (gateway → commerce → catalog) ==="
code=$(curl -sS -m 15 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${COMMERCE_PORT}/api/v1/order-returns" 2>/dev/null || echo ERR)
if [ "$code" = "401" ] || [ "$code" = "200" ]; then
  echo "OK   GET /api/v1/order-returns on commerce (${code})"
else
  echo "FAIL GET /api/v1/order-returns on commerce (${code}) — restart with uat-restart-isolated-commerce.sh" >&2
  FAIL=1
fi
gcode=$(curl -sS -m 15 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${GATEWAY_PORT}/api/v1/order-returns" 2>/dev/null || echo ERR)
if [ "$gcode" = "401" ] || [ "$gcode" = "200" ]; then
  echo "OK   GET /api/v1/order-returns via gateway (${gcode})"
elif [ "$gcode" = "503" ] || [ "$gcode" = "502" ]; then
  echo "FAIL GET /api/v1/order-returns via gateway (${gcode}) — commerce down or wrong catalog URL" >&2
  FAIL=1
else
  echo "WARN GET /api/v1/order-returns via gateway (${gcode})" >&2
fi

echo ""
echo "=== Nginx → gateway ==="
for path in /api/v1/categories /management/health/liveness; do
  code=$(curl -sS -m 15 -o /dev/null -w "%{http_code}" "https://uat.minisnmore.com${path}" 2>/dev/null || echo ERR)
  if [ "$code" = "200" ]; then
    echo "OK   https://uat.minisnmore.com${path} (${code})"
  else
    echo "FAIL https://uat.minisnmore.com${path} (${code})" >&2
    FAIL=1
  fi
done

echo ""
echo "=== OAuth fix in gateway jar ==="
REPO="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
if strings "${REPO}/deploy/gateway-uat.jar" 2>/dev/null | grep -qF "ON CONFLICT"; then
  echo "OK   gateway-uat.jar contains idempotent jhi_user_authority insert"
else
  echo "WARN gateway-uat.jar missing ON CONFLICT (rebuild gateway + copy to deploy/)" >&2
fi

if [ "$FAIL" -ne 0 ]; then
  echo ""
  echo "Fix: REPO_ROOT=${REPO} bash deploy/scripts/uat-microservices-isolated-pm2.sh"
  echo "     ISOLATED_GATEWAY_PORT=9180 bash deploy/scripts/uat-nginx-cutover-isolated.sh"
  exit 1
fi
echo ""
echo "All isolated UAT checks passed."
