#!/usr/bin/env bash
# Smoke-test admin JWT on the isolated UAT gateway (:9180 by default).
# Usage: GATEWAY_URL=http://127.0.0.1:9180 bash deploy/scripts/uat-isolated-admin-smoke.sh
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:9180}"
ADMIN_USER="${ADMIN_USER:-admin}"
ADMIN_PASS="${ADMIN_PASS:-${PROD_ADMIN_PASSWORD:-}}"

check() {
  local name="$1"
  local url="$2"
  local code
  code=$(curl -s -o /dev/null -w '%{http_code}' -H "Authorization: Bearer ${TOKEN}" "$url")
  if [[ "$code" =~ ^2 ]]; then
    echo "OK  $name ($code) $url"
  else
    echo "FAIL $name ($code) $url" >&2
    FAIL=1
  fi
}

FAIL=0

echo "Gateway: $GATEWAY_URL"
echo "Auth as: $ADMIN_USER"

AUTH_JSON=$(curl -s -X POST "${GATEWAY_URL}/api/authenticate" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"${ADMIN_USER}\",\"password\":\"${ADMIN_PASS}\",\"rememberMe\":false}")

TOKEN=$(python3 -c "import json,sys; d=json.load(sys.stdin); print(d.get('id_token') or d.get('access_token') or '')" <<<"$AUTH_JSON" 2>/dev/null || true)

if [ -z "$TOKEN" ]; then
  echo "FAIL authenticate — response: ${AUTH_JSON:0:200}" >&2
  exit 1
fi
echo "OK  authenticate (got JWT)"

check "storefront categories" "${GATEWAY_URL}/api/v1/categories"
check "site-settings" "${GATEWAY_URL}/api/v1/site-settings"
check "admin dashboard" "${GATEWAY_URL}/api/v1/admin/dashboard"
check "admin zoho-settings" "${GATEWAY_URL}/api/v1/admin/zoho-settings"
check "admin zoho integration-status" "${GATEWAY_URL}/api/v1/admin/zoho/integration-status"
check "admin zoho product-sync-status" "${GATEWAY_URL}/api/v1/admin/zoho/product-sync-status"
# Legacy monolith paths (404 expected after microservice split):
legacy=$(curl -sS -m 15 -o /dev/null -w "%{http_code}" "${GATEWAY_URL}/api/products" || echo ERR)
if [ "$legacy" = "404" ] || [ "$legacy" = "401" ] || [ "$legacy" = "403" ]; then
  echo "OK  legacy /api/products → ${legacy} (use /api/v1/products)"
else
  echo "WARN legacy /api/products → ${legacy} (expected 404)" >&2
fi
ZCODE=$(curl -s -o /dev/null -w '%{http_code}' -X POST -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" -d '{}' "${GATEWAY_URL}/api/v1/admin/zoho/retry-orders")
if [[ "$ZCODE" =~ ^2 ]]; then
  echo "OK  admin zoho retry-orders ($ZCODE) ${GATEWAY_URL}/api/v1/admin/zoho/retry-orders"
else
  echo "FAIL admin zoho retry-orders ($ZCODE) ${GATEWAY_URL}/api/v1/admin/zoho/retry-orders" >&2
  FAIL=1
fi

if [ "$FAIL" -ne 0 ]; then
  echo "" >&2
  echo "403 on admin routes usually means store/integration need JWT claim 'auth':" >&2
  echo "  export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITIES_CLAIM_NAME=auth" >&2
  echo "  export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITY_PREFIX=" >&2
  echo "  pm2 restart mini-prod-ms-store mini-prod-ms-integration --update-env" >&2
  exit 1
fi

echo ""
echo "Admin JWT smoke tests passed."
