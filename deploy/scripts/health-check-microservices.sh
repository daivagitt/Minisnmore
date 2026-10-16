#!/usr/bin/env bash
# Health check for gateway + catalog (run on host where services listen on localhost).
set -euo pipefail

GATEWAY_URL="${GATEWAY_URL:-http://127.0.0.1:9180}"
CATALOG_CORE_URL="${CATALOG_CORE_URL:-http://127.0.0.1:9184}"
STORE_URL="${STORE_URL:-http://127.0.0.1:9181}"
COMMERCE_URL="${COMMERCE_URL:-http://127.0.0.1:9182}"
INTEGRATION_URL="${INTEGRATION_URL:-}"
FAIL=0

check() {
  local name="$1"
  local url="$2"
  if curl -fsS "$url" >/dev/null; then
    echo "OK  $name ($url)"
  else
    echo "FAIL $name ($url)" >&2
    FAIL=1
  fi
}

check "gateway liveness" "$GATEWAY_URL/management/health/liveness"
check "gateway readiness" "$GATEWAY_URL/management/health/readiness"
check "catalog core liveness" "$CATALOG_CORE_URL/management/health/liveness"
check "catalog core readiness" "$CATALOG_CORE_URL/management/health/readiness"
check "store liveness" "$STORE_URL/management/health/liveness"
check "store readiness" "$STORE_URL/management/health/readiness"
check "commerce liveness" "$COMMERCE_URL/management/health/liveness"
check "commerce readiness" "$COMMERCE_URL/management/health/readiness"
if [ -n "$INTEGRATION_URL" ]; then
  check "integration liveness" "$INTEGRATION_URL/management/health/liveness"
  check "integration readiness" "$INTEGRATION_URL/management/health/readiness"
fi
check "storefront via gateway" "$GATEWAY_URL/api/v1/categories"

if [ "$FAIL" -ne 0 ]; then
  exit 1
fi
echo "All microservice health checks passed."
