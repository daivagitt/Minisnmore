#!/usr/bin/env bash
# Quick Zoho production health check — run on VPS after JAR deploy / PM2 restart.
#   bash /var/www/uat1/mini_ms_test/deploy/scripts/prod-verify-zoho-stack.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
CATALOG_PORT="${CATALOG_PORT:-9184}"

# Prefer encrypted prod.env.enc
# shellcheck source=lib/encrypted-env.sh
source "${SCRIPT_DIR}/lib/encrypted-env.sh"
if ! mini_load_host_env prod; then
  echo "ERROR: could not load host env (prod.env.enc or prod.env)." >&2
  exit 1
fi

FAIL=0

warn() { echo "WARN: $*" >&2; FAIL=1; }
ok() { echo "OK  $*"; }

echo "=== JAR ages (new deploy should be recent) ==="
for label_path in \
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
echo "=== Core integration runtime ==="
echo "Zoho integration runs inside catalog core on :${CATALOG_PORT}; no separate Integration JAR is required."
echo ""
echo "=== Liveness ==="
for label_port in "gateway:${GATEWAY_PORT}" "catalog:${CATALOG_PORT}"; do
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
  else
    warn "Admin authentication failed; could not inspect integration status"
  fi
else
  echo "Set PROD_ADMIN_EMAIL and PROD_ADMIN_PASSWORD to inspect authenticated integration status"
fi

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
