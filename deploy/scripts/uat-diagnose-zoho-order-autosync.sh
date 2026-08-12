#!/usr/bin/env bash
# Why paid checkout orders may not appear in Zoho Books (catalog → integration internal trigger).
# Usage on VPS:
#   bash /var/www/uat1/mini_ms_test/deploy/scripts/uat-diagnose-zoho-order-autosync.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

GATEWAY_PORT="${GATEWAY_PORT:-9180}"
COMMERCE_PORT="${COMMERCE_PORT:-9182}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

INTERNAL_API_KEY="${INTERNAL_API_KEY:-uat-ms-isolated-internal-key}"

echo "=== Checkout → Zoho chain (isolated stack) ==="
echo "  Browser POST /api/v1/orders/verify-payment"
echo "    → gateway :${GATEWAY_PORT} → commerce :${COMMERCE_PORT} → catalog :${CATALOG_PORT}"
echo "    → catalog calls integration :${INTEGRATION_PORT}/internal/v1/zoho/triggers/sync-orders/{id}"
echo ""

echo "=== Service liveness ==="
for label_port in "gateway:${GATEWAY_PORT}" "commerce:${COMMERCE_PORT}" "catalog:${CATALOG_PORT}" "integration:${INTEGRATION_PORT}"; do
  label="${label_port%%:*}"
  port="${label_port##*:}"
  code=$(curl -sS -m 5 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${port}/management/health/liveness" || echo ERR)
  echo "${label} :${port} → ${code}"
done

echo ""
echo "=== PM2 env (catalog must point at integration :${INTEGRATION_PORT}) ==="
pm2 describe mini-uat-ms-catalog-core 2>/dev/null | grep -E 'zoho-integration|internal-api-key|catalog-base' || echo "WARN: mini-uat-ms-catalog-core not in pm2"
pm2 describe mini-uat-ms-commerce 2>/dev/null | grep -E 'catalog-base-url' || echo "WARN: mini-uat-ms-commerce not in pm2"
pm2 describe mini-uat-ms-integration 2>/dev/null | grep -E 'internal-api-key' || true

echo ""
echo "=== Internal trigger smoke (integration only) ==="
# Pick latest paid order missing Zoho SO if psql available
ORDER_ID=""
if [ -n "${SPRING_DATASOURCE_USERNAME:-}" ] && [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  ORDER_ID=$(PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql -h 127.0.0.1 -U "$SPRING_DATASOURCE_USERNAME" -d miniandmore -tA -c \
    "SELECT id FROM customer_order WHERE payment_status = 'PAID' AND (zoho_sales_order_id IS NULL OR zoho_sales_order_id = '') ORDER BY id DESC LIMIT 1;" 2>/dev/null || true)
  ORDER_ID=$(echo "$ORDER_ID" | tr -d '[:space:]')
fi
if [ -n "$ORDER_ID" ]; then
  echo "Latest paid order without Zoho SO: id=${ORDER_ID}"
  TRIGGER_CODE=$(curl -sS -m 120 -o /tmp/zoho-trigger-body.txt -w "%{http_code}" -X POST \
    -H "X-Internal-Api-Key: ${INTERNAL_API_KEY}" \
    -H "Content-Type: application/json" \
    "http://127.0.0.1:${INTEGRATION_PORT}/internal/v1/zoho/triggers/sync-orders/${ORDER_ID}" || echo ERR)
  echo "POST internal sync-orders/${ORDER_ID} → ${TRIGGER_CODE}"
  head -c 300 /tmp/zoho-trigger-body.txt 2>/dev/null || true
  echo ""
else
  echo "No sample order id (set DB creds in uat.env or pass ORDER_ID=123)"
fi

echo ""
echo "=== Recent catalog Zoho sync log lines ==="
pm2 logs mini-uat-ms-catalog-core --nostream --lines 80 2>/dev/null | grep -iE 'Zoho Books order sync|zoho-integration|Connection refused|8083|9183' | tail -15 || echo "(no matching catalog logs)"

echo ""
echo "=== Recent integration order sync log lines ==="
pm2 logs mini-uat-ms-integration --nostream --lines 80 2>/dev/null | grep -iE 'syncOrder|sales order|Zoho.*order' | tail -15 || echo "(no matching integration logs)"

echo ""
echo "=== Paid orders missing Zoho (DB) ==="
if [ -n "${SPRING_DATASOURCE_USERNAME:-}" ] && [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  PGPASSWORD="$SPRING_DATASOURCE_PASSWORD" psql -h 127.0.0.1 -U "$SPRING_DATASOURCE_USERNAME" -d miniandmore -c \
    "SELECT id, order_number, payment_status, zoho_sync_status, left(coalesce(zoho_sync_error,''),80) AS err
     FROM customer_order
     WHERE payment_status = 'PAID' AND (zoho_sales_order_id IS NULL OR zoho_sales_order_id = '')
     ORDER BY id DESC LIMIT 10;" 2>/dev/null || echo "WARN: could not query DB"
else
  echo "Set SPRING_DATASOURCE_USERNAME/PASSWORD in uat.env to list pending orders"
fi

echo ""
echo "=== Fixes if autosync fails ==="
echo "1. Restart catalog with integration URL (most common on isolated UAT):"
echo "   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-restart-isolated-catalog.sh"
echo "2. Restart commerce (must proxy to catalog :${CATALOG_PORT}):"
echo "   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-restart-isolated-commerce.sh"
echo "3. Ensure nginx /api/ → :${GATEWAY_PORT} (not monolith :8081):"
echo "   bash deploy/scripts/uat-find-nginx-config.sh"
echo "4. Admin → Zoho: connected + products synced to Zoho (items need SKU in Zoho)"
echo "5. Retry pending orders: Admin → Zoho → Sync orders to Zoho (or by order number)"
