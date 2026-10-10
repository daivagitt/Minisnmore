#!/usr/bin/env bash
# Remove broken SPRING_CLOUD_GATEWAY route overrides from uat.env (gateway boot crash).
# Run on UAT VPS as root:
#   bash /var/www/uat1/mini_ms_test/deploy/scripts/uat-fix-gateway-route-env.sh
set -euo pipefail

UAT_ENV="${UAT_ENV:-/etc/mini-and-more/uat.env}"

if [ ! -f "$UAT_ENV" ]; then
  echo "ERROR: missing ${UAT_ENV}" >&2
  exit 1
fi

if grep -qE '^SPRING_CLOUD_GATEWAY.*ROUTES' "$UAT_ENV"; then
  BACKUP="${UAT_ENV}.bak-gateway-routes-$(date +%Y%m%d-%H%M%S)"
  cp -a "$UAT_ENV" "$BACKUP"
  echo "Backup: ${BACKUP}"
  grep -E '^SPRING_CLOUD_GATEWAY.*ROUTES' "$UAT_ENV" || true
  sed -i '/^SPRING_CLOUD_GATEWAY.*ROUTES/d' "$UAT_ENV"
  echo "Removed SPRING_CLOUD_GATEWAY*ROUTES* lines from ${UAT_ENV}"
else
  echo "OK  no SPRING_CLOUD_GATEWAY route lines in ${UAT_ENV}"
fi

# Ensure MINIANDMORE_*_URI lines exist (commented template if missing)
for line in \
  'MINIANDMORE_STORE_URI=http://127.0.0.1:9181' \
  'MINIANDMORE_COMMERCE_URI=http://127.0.0.1:9182' \
  'MINIANDMORE_INTEGRATION_URI=http://127.0.0.1:9183' \
  'GATEWAY_RESPONSE_TIMEOUT=600s'; do
  key="${line%%=*}"
  if ! grep -q "^${key}=" "$UAT_ENV" 2>/dev/null; then
    echo "$line" >> "$UAT_ENV"
    echo "Added ${key} to ${UAT_ENV}"
  fi
done

echo ""
echo "Restart gateway:"
echo "  bash /var/www/uat1/mini_ms_test/deploy/scripts/uat-restart-isolated-gateway.sh"
