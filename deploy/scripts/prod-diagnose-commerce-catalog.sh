#!/usr/bin/env bash
# Trace 503 on /api/v1/admin/order-returns (commerce → catalog).
# Usage on VPS:
#   REPO_ROOT=/var/www/uat1/mini_ms_test \
#   PROD_ADMIN_EMAIL=admin@minisnmore.com PROD_ADMIN_PASSWORD=... \
#   bash deploy/scripts/prod-diagnose-commerce-catalog.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

GATEWAY_PORT="${GATEWAY_PORT:-9180}"
COMMERCE_PORT="${COMMERCE_PORT:-9182}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
ADMIN_EMAIL="${PROD_ADMIN_EMAIL:-admin@minisnmore.com}"
ADMIN_PASSWORD="${PROD_ADMIN_PASSWORD:-}"

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi

echo "=== Env (catalog URL seen by commerce) ==="
echo "APPLICATION_CATALOG_BASE_URL=${APPLICATION_CATALOG_BASE_URL:-<unset>}"
echo "CATALOG_BASE_URL=${CATALOG_BASE_URL:-<unset>}"

echo ""
echo "=== Liveness ==="
for label_port in "catalog:${CATALOG_PORT}" "commerce:${COMMERCE_PORT}" "gateway:${GATEWAY_PORT}"; do
  label="${label_port%%:*}"
  port="${label_port##*:}"
  code=$(curl -sS -m 5 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${port}/management/health/liveness" || echo ERR)
  echo "${label} :${port} → ${code}"
done

echo ""
echo "=== Admin JWT (gateway) ==="
if [ -z "$ADMIN_PASSWORD" ]; then
  echo "FAIL set PROD_ADMIN_PASSWORD to run authenticated hops" >&2
  exit 1
fi
AUTH_BODY=$(printf '{"username":"%s","password":"%s"}' "$ADMIN_EMAIL" "$ADMIN_PASSWORD")
TOKEN=$(curl -sS -m 15 -X POST "http://127.0.0.1:${GATEWAY_PORT}/api/authenticate" \
  -H "Content-Type: application/json" \
  -d "$AUTH_BODY" | sed -n 's/.*"id_token"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p')
if [ -z "$TOKEN" ]; then
  echo "FAIL could not obtain id_token for ${ADMIN_EMAIL}" >&2
  exit 1
fi
echo "OK  id_token length ${#TOKEN}"

PATH_QS="/api/v1/admin/order-returns?status=requested"
INTERNAL_QS="/internal/v1/admin/order-returns?status=requested"

check_authed() {
  local label="$1"
  local url="$2"
  local code
  code=$(curl -sS -m 30 -o /dev/null -w "%{http_code}" -H "Authorization: Bearer ${TOKEN}" "http://127.0.0.1:${url}" || echo ERR)
  echo "${label} → ${code}"
  if [ "$code" = "200" ]; then
    return 0
  fi
  return 1
}

echo ""
echo "=== Authenticated admin order-returns (expect 200) ==="
FAIL=0
check_authed "catalog core ${INTERNAL_QS}" "${CATALOG_PORT}${INTERNAL_QS}" || FAIL=1
check_authed "commerce ${PATH_QS}" "${COMMERCE_PORT}${PATH_QS}" || FAIL=1
check_authed "gateway ${PATH_QS}" "${GATEWAY_PORT}${PATH_QS}" || FAIL=1

PUBLIC_CODE=$(curl -sS -m 10 -o /dev/null -w "%{http_code}" "https://minisnmore.com${PATH_QS}" \
  -H "Authorization: Bearer ${TOKEN}" 2>/dev/null || echo ERR)
echo "public https://minisnmore.com${PATH_QS} → ${PUBLIC_CODE}"

if [ "$FAIL" -ne 0 ]; then
  echo ""
  echo "If catalog=200 but commerce/gateway=502/503, commerce likely points at wrong catalog port." >&2
  echo "Fix /etc/mini-and-more/prod.env:" >&2
  echo "  APPLICATION_CATALOG_BASE_URL=http://127.0.0.1:${CATALOG_PORT}" >&2
  echo "Then: REPO_ROOT=${REPO_ROOT:-/var/www/uat1/mini_ms_test} bash deploy/scripts/prod-restart-isolated-commerce.sh" >&2
  exit 1
fi

echo ""
echo "All hops returned 200."

echo ""
echo "=== Sample product (for order POST) ==="
PRODUCT_JSON=$(curl -sS -m 15 "http://127.0.0.1:${GATEWAY_PORT}/api/v1/products?size=1" || echo '{}')
echo "$PRODUCT_JSON" | head -c 600
echo ""

PRODUCT_ID=$(echo "$PRODUCT_JSON" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)
PRODUCT_SLUG=$(echo "$PRODUCT_JSON" | sed -n 's/.*"slug"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
PRODUCT_NAME=$(echo "$PRODUCT_JSON" | sed -n 's/.*"name"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' | head -1)
PRODUCT_PRICE=$(echo "$PRODUCT_JSON" | sed -n 's/.*"price"[[:space:]]*:[[:space:]]*\([0-9][0-9]*\).*/\1/p' | head -1)

if [ -z "$PRODUCT_ID" ]; then
  echo "WARN: could not parse a product id — order POST will use placeholder id 1" >&2
  PRODUCT_ID=1
  PRODUCT_SLUG=test
  PRODUCT_NAME=Test
  PRODUCT_PRICE=349
fi

if command -v jq >/dev/null 2>&1; then
  ORDER_BODY=$(jq -n \
    --arg product_id "$PRODUCT_ID" \
    --arg product_slug "$PRODUCT_SLUG" \
    --arg product_name "$PRODUCT_NAME" \
    --argjson price "${PRODUCT_PRICE:-349}" \
    '{
      items: [{
        product_id: $product_id,
        product_slug: $product_slug,
        product_name: $product_name,
        qty: 1,
        price: $price,
        size: "One Size",
        color: "Natural"
      }],
      shipping_address: {
        full_name: "Diag",
        email: "diag@example.com",
        phone: "9999999999",
        line1: "Line 1",
        city: "Hyderabad",
        state: "Telangana",
        pincode: "500001"
      },
      shipping_mode: "std",
      coupon_code: "",
      donation: 0
    }')
else
  echo "ERROR: jq is required to build order JSON safely" >&2
  exit 1
fi

post_order() {
  local label="$1"
  local url="$2"
  echo "--- POST orders ${label} ---"
  curl -sS -m 60 -w "\nHTTP %{http_code}\n" -X POST "http://127.0.0.1:${url}" \
    -H "Authorization: Bearer ${TOKEN}" \
    -H "Content-Type: application/json" \
    -d "$ORDER_BODY" | tail -8
  echo ""
}

echo ""
echo "=== POST /orders (catalog → commerce → gateway) ==="
post_order "catalog ${CATALOG_PORT}/internal/v1/orders" "${CATALOG_PORT}/internal/v1/orders"
post_order "commerce ${COMMERCE_PORT}/api/v1/orders" "${COMMERCE_PORT}/api/v1/orders"
post_order "gateway ${GATEWAY_PORT}/api/v1/orders" "${GATEWAY_PORT}/api/v1/orders"

echo "=== commerce pm2 args (catalog-base-url) ==="
pm2 describe mini-prod-ms-commerce 2>/dev/null | grep -E 'catalog-base-url|script path' || true

echo ""
echo "Interpretation:"
echo "  catalog=400/502, commerce/gateway=503 → redeploy commerce JAR (proxy drops errors) or wrong catalog URL"
echo "  catalog=502 'Could not initialize payment' → fix RAZORPAY_* in prod.env (no inline # comments)"
echo "  catalog=400 'Product not found' → use a real product id from GET /api/v1/products"
