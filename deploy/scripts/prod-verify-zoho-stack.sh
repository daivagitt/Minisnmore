#!/usr/bin/env bash
# Quick Zoho production health check — run on VPS after JAR deploy / PM2 restart.
#   bash /var/www/uat1/mini_ms_test/deploy/scripts/prod-verify-zoho-stack.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"
CATALOG_PORT="${CATALOG_PORT:-9184}"

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi

INTERNAL_API_KEY="${INTERNAL_API_KEY:-uat-ms-isolated-internal-key}"
FAIL=0

warn() { echo "WARN: $*" >&2; FAIL=1; }
ok() { echo "OK  $*"; }

echo "=== JAR ages (new deploy should be recent) ==="
for label_path in \
  "integration:${REPO_ROOT}/integration/target/miniandmore-integration-"*.jar \
  "catalog:${REPO_ROOT}/backend/target/miniandmore-"*.jar; do
  label="${label_path%%:*}"
  path="${label_path#*:}"
  if [ -f "$path" ]; then
    ls -la "$path"
  else
    warn "$label JAR missing at ${REPO_ROOT}"
  fi
done

echo ""
echo "=== PM2 catalog args (must NOT contain 9183-Djhipster) ==="
CATALOG_ARGS=$(pm2 describe mini-prod-ms-catalog-core 2>/dev/null | grep 'script args' || true)
echo "${CATALOG_ARGS:-catalog not in pm2}"
if echo "$CATALOG_ARGS" | grep -qE '9183-[^/]|8083-D'; then
  warn "Catalog zoho-integration URL is MALFORMED in PM2 (missing space between -D flags). Run:"
  echo "  REPO_ROOT=${REPO_ROOT} bash ${REPO_ROOT}/deploy/scripts/prod-restart-isolated-catalog.sh"
elif echo "$CATALOG_ARGS" | grep -qE 'zoho-integration\.base-url=http://127\.0\.0\.1:9183[^0-9/]'; then
  warn "Catalog -D zoho URL may be truncated — prefer env ZOHO_INTEGRATION_BASE_URL via prod-restart-isolated-catalog.sh"
elif ! pm2 env mini-prod-ms-catalog-core 2>/dev/null | grep -q 'ZOHO_INTEGRATION_BASE_URL=http://127.0.0.1:9183'; then
  if ! echo "$CATALOG_ARGS" | grep -q 'zoho-integration.base-url=http://127.0.0.1:9183'; then
    warn "Catalog not configured for integration :9183 (env ZOHO_INTEGRATION_BASE_URL or -D property)"
  else
    ok "catalog points at :9183 via -D property"
  fi
else
  ok "catalog ZOHO_INTEGRATION_BASE_URL=http://127.0.0.1:9183"
fi

echo ""
echo "=== Liveness ==="
for label_port in "gateway:${GATEWAY_PORT}" "integration:${INTEGRATION_PORT}" "catalog:${CATALOG_PORT}"; do
  label="${label_port%%:*}"
  port="${label_port##*:}"
  code=$(curl -sS -m 5 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${port}/management/health/liveness" || echo ERR)
  if [ "$code" = "200" ]; then ok "$label :${port}"; else warn "$label :${port} → ${code}"; fi
done

echo ""
echo "=== Zoho integration status (admin JWT optional) ==="
if command -v jq >/dev/null 2>&1 && [ -n "${PROD_ADMIN_PASSWORD:-}" ]; then
  ADMIN_EMAIL="${PROD_ADMIN_EMAIL:-admin@minisnmore.com}"
  AUTH_BODY=$(jq -n --arg u "$ADMIN_EMAIL" --arg p "$PROD_ADMIN_PASSWORD" '{username:$u,password:$p}')
  TOKEN=$(curl -fsS -m 15 -X POST "http://127.0.0.1:${GATEWAY_PORT}/api/authenticate" \
    -H "Content-Type: application/json" -d "$AUTH_BODY" | jq -r '.id_token // empty' || true)
  if [ -n "$TOKEN" ]; then
    curl -fsS -m 30 -H "Authorization: Bearer ${TOKEN}" \
      "http://127.0.0.1:${GATEWAY_PORT}/api/v1/admin/zoho/integration-status" | head -c 400
    echo ""
    code=$(curl -sS -m 120 -o /tmp/zoho-retry.json -w "%{http_code}" -X POST \
      -H "Authorization: Bearer ${TOKEN}" -H "Content-Type: application/json" \
      -d '{}' "http://127.0.0.1:${GATEWAY_PORT}/api/v1/admin/zoho/retry-orders" || echo ERR)
    echo "POST retry-orders → ${code}"
    head -c 300 /tmp/zoho-retry.json 2>/dev/null; echo ""
    if [ "$code" = "500" ]; then
      warn "retry-orders 500 — integration JAR may be OLD (readOnly bug). Rebuild integration + restart."
    elif [ "$code" = "403" ]; then
      warn "retry-orders 403 — stale JVM on :9183 or JWT mismatch. Restart isolated integration via prod-microservices-isolated-pm2.sh"
    elif [ "$code" = "405" ]; then
      warn "retry-orders 405 — caller using GET; use POST (redeploy frontend or use curl -X POST)"
    fi
  else
    warn "no admin JWT (set PROD_ADMIN_PASSWORD)"
  fi
else
  echo "Skip admin API checks (need jq + PROD_ADMIN_PASSWORD)"
fi

echo ""
echo "=== Internal order sync smoke ==="
ORDER_ID=""
if [ -n "${SPRING_DATASOURCE_USERNAME:-}" ] && [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  ORDER_ID=$(PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql -h 127.0.0.1 -U "$SPRING_DATASOURCE_USERNAME" -d miniandmore -tA -c \
    "SELECT id FROM customer_order WHERE payment_status = 'PAID' AND (zoho_sales_order_id IS NULL OR zoho_sales_order_id = '') ORDER BY id DESC LIMIT 1;" 2>/dev/null || true)
  ORDER_ID=$(echo "$ORDER_ID" | tr -d '[:space:]')
fi
if [ -n "$ORDER_ID" ]; then
  code=$(curl -sS -m 120 -o /tmp/zoho-sync-order.txt -w "%{http_code}" -X POST \
    -H "X-Internal-Api-Key: ${INTERNAL_API_KEY}" \
    "http://127.0.0.1:${INTEGRATION_PORT}/internal/v1/zoho/triggers/sync-orders/${ORDER_ID}" || echo ERR)
  echo "POST internal sync-orders/${ORDER_ID} → ${code}"
  head -c 400 /tmp/zoho-sync-order.txt 2>/dev/null; echo ""
else
  echo "No paid order missing Zoho SO in DB (or DB creds missing)"
fi

echo ""
echo "=== Recent integration Zoho log lines ==="
pm2 logs mini-prod-ms-integration --nostream --lines 60 2>/dev/null \
  | grep -iE 'Zoho|429|rate limit|OAuth|syncOrder|sales order|ERROR' | tail -20 || echo "(none)"

echo ""
echo "=== Recent catalog Zoho sync failures ==="
pm2 logs mini-prod-ms-catalog-core --nostream --lines 60 2>/dev/null \
  | grep -iE 'Zoho Books order sync failed|Connection refused|9183|8083' | tail -15 || echo "(none)"

echo ""
echo "=== Paid orders still missing Zoho (last 5) ==="
if [ -n "${SPRING_DATASOURCE_USERNAME:-}" ] && [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql -h 127.0.0.1 -U "$SPRING_DATASOURCE_USERNAME" -d miniandmore -c \
    "SELECT id, order_number, zoho_sync_status, left(coalesce(zoho_sync_error,''),100) AS err
     FROM customer_order WHERE payment_status = 'PAID' AND (zoho_sales_order_id IS NULL OR zoho_sales_order_id = '')
     ORDER BY id DESC LIMIT 5;" 2>/dev/null || warn "DB query failed"
else
  echo "Set DB creds in prod.env to list pending orders"
fi

echo ""
if [ "$FAIL" -ne 0 ]; then
  echo "=== Some checks failed — see WARN lines above ==="
  exit 1
fi
echo "=== All automated checks passed (Zoho API may still fail on 429 / missing SKUs) ==="
