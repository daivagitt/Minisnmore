#!/usr/bin/env bash
# Smoke-check paths the React app uses (run with gateway + catalog up).
set -euo pipefail

BASE="${API_BASE:-http://127.0.0.1:8080}"
FAIL=0

check() {
  local name="$1"
  local method="$2"
  local path="$3"
  local extra="${4:-}"
  local code
  code=$(curl -sS -o /dev/null -w "%{http_code}" -X "$method" $extra "$BASE$path")
  if [[ "$code" =~ ^(200|201|204)$ ]]; then
    echo "OK  $method $path ($code)"
  else
    echo "FAIL $method $path ($code)" >&2
    FAIL=1
  fi
}

echo "Verifying public catalog paths at $BASE ..."

check "categories" GET "/api/v1/categories"
check "products" GET "/api/v1/products"
check "blog" GET "/api/v1/blog-posts"
check "banners" GET "/api/v1/banners"
check "home-sections" GET "/api/v1/home-sections"
check "page-visibility" GET "/api/v1/page-visibility"
check "site-settings" GET "/api/v1/site-settings"
check "subscribe-page" GET "/api/v1/subscribe-page"

# Commerce fields must be present for checkout
BODY=$(curl -fsS "$BASE/api/v1/site-settings" | python3 -c "
import json,sys
o=json.load(sys.stdin)
d=o.get('data') or o
for k in ('gstRate','shippingStandardInr','freeShippingMinInr'):
    if k not in d and d.get(k) is None:
        print('missing', k)
        sys.exit(1)
print('commerce-ok')
" 2>/dev/null) || { echo "FAIL site-settings commerce fields" >&2; FAIL=1; }
[[ "${BODY:-}" == "commerce-ok" ]] && echo "OK  site-settings includes commerce fields"

check "coupon validate" POST "/api/v1/coupons/validate" "-H 'Content-Type: application/json' -d '{\"code\":\"WELCOME10\",\"subtotal\":1000}'"

if [[ "$FAIL" -ne 0 ]]; then
  echo "Start stack: bash scripts/dev-start-microservices.sh" >&2
  exit 1
fi
echo "Frontend API contract smoke checks passed."
