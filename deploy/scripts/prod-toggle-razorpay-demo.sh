#!/usr/bin/env bash
# Toggle Razorpay demo bypass on UAT catalog (payment verify + refunds without live Razorpay).
#
#   bash deploy/scripts/prod-toggle-razorpay-demo.sh demo   # enable demo for CI/scenario tests
#   bash deploy/scripts/prod-toggle-razorpay-demo.sh live   # restore live payment verify
#
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

MODE="${1:-}"
ENV_FILE="${PROD_ENV_FILE:-/etc/mini-and-more/prod.env}"
REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found" >&2
  exit 1
fi

case "$MODE" in
  demo)
    VALUE=true
    LABEL="demo (bypass Razorpay verify/refund)"
    ;;
  live)
    VALUE=false
    LABEL="live (real Razorpay verify/refund)"
    ;;
  *)
    echo "Usage: $0 demo|live" >&2
    exit 1
    ;;
esac

cp -a "$ENV_FILE" "${ENV_FILE}.bak-$(date +%Y%m%d%H%M)-razorpay-${MODE}"

set_kv() {
  local key="$1"
  if grep -q "^${key}=" "$ENV_FILE"; then
    sed -i "s/^${key}=.*/${key}=${VALUE}/" "$ENV_FILE"
  else
    echo "${key}=${VALUE}" >> "$ENV_FILE"
  fi
}

set_kv RAZORPAY_PAYMENT_DEMO
set_kv RAZORPAY_REFUND_DEMO

echo "=== Razorpay mode: $LABEL ==="
echo "Updated $ENV_FILE (backup: ${ENV_FILE}.bak-*-razorpay-${MODE})"
grep -E '^RAZORPAY_(PAYMENT|REFUND)_DEMO=' "$ENV_FILE"

echo "=== Restart catalog core to apply ==="
bash "$REPO_ROOT/deploy/scripts/prod-reload-env-and-restart.sh" mini-prod-ms-catalog-core

echo "OK  Catalog restarted with RAZORPAY_PAYMENT_DEMO=${VALUE} RAZORPAY_REFUND_DEMO=${VALUE}"
