#!/usr/bin/env bash
# Remove broken SPRING_CLOUD_GATEWAY route overrides from prod.env (gateway boot crash).
# Run on production VPS as root:
#   bash /var/www/uat1/mini_ms_test/deploy/scripts/prod-fix-gateway-route-env.sh
set -euo pipefail

PROD_ENV="${PROD_ENV:-/etc/mini-and-more/prod.env}"

if [ ! -f "$PROD_ENV" ]; then
  echo "ERROR: missing ${PROD_ENV}" >&2
  exit 1
fi

if grep -qE '^SPRING_CLOUD_GATEWAY.*ROUTES' "$PROD_ENV"; then
  BACKUP="${PROD_ENV}.bak-gateway-routes-$(date +%Y%m%d-%H%M%S)"
  cp -a "$PROD_ENV" "$BACKUP"
  echo "Backup: ${BACKUP}"
  grep -E '^SPRING_CLOUD_GATEWAY.*ROUTES' "$PROD_ENV" || true
  sed -i '/^SPRING_CLOUD_GATEWAY.*ROUTES/d' "$PROD_ENV"
  echo "Removed SPRING_CLOUD_GATEWAY*ROUTES* lines from ${PROD_ENV}"
else
  echo "OK  no SPRING_CLOUD_GATEWAY route lines in ${PROD_ENV}"
fi

# Ensure MINIANDMORE_*_URI lines exist (commented template if missing)
for line in \
  'MINIANDMORE_STORE_URI=http://127.0.0.1:9181' \
  'MINIANDMORE_COMMERCE_URI=http://127.0.0.1:9182' \
  'MINIANDMORE_INTEGRATION_URI=http://127.0.0.1:9183' \
  'GATEWAY_RESPONSE_TIMEOUT=600s'; do
  key="${line%%=*}"
  if ! grep -q "^${key}=" "$PROD_ENV" 2>/dev/null; then
    echo "$line" >> "$PROD_ENV"
    echo "Added ${key} to ${PROD_ENV}"
  fi
done

echo ""
echo "Restart gateway:"
echo "  bash /var/www/uat1/mini_ms_test/deploy/scripts/prod-restart-isolated-gateway.sh"
