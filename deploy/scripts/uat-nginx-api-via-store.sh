#!/usr/bin/env bash
# Emergency: proxy public /api/ to store :9181 (store is healthy; gateway :9180 hung).
# OAuth still uses :9180. Revert with uat-nginx-api-via-store.sh --revert
set -euo pipefail

NGINX_FILE="${NGINX_FILE:-/etc/nginx/sites-available/mini-frontend-uat}"
STORE_PORT="${STORE_PORT:-9181}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"

if [ ! -f "$NGINX_FILE" ]; then
  echo "ERROR: $NGINX_FILE not found" >&2
  exit 1
fi

if [ "${1:-}" = "--revert" ]; then
  sed -i "s|127.0.0.1:${STORE_PORT}/api/|127.0.0.1:${GATEWAY_PORT}/api/|g" "$NGINX_FILE"
  nginx -t && systemctl reload nginx
  echo "Reverted /api/ → gateway :${GATEWAY_PORT}"
  exit 0
fi

cp -a "$NGINX_FILE" "${NGINX_FILE}.bak-store-api-$(date +%Y%m%d-%H%M%S)"
sed -i "s|127.0.0.1:${GATEWAY_PORT}/api/|127.0.0.1:${STORE_PORT}/api/|g" "$NGINX_FILE"
nginx -t && systemctl reload nginx
echo "API now → store :${STORE_PORT} (catalog/browse works; orders/oauth may need gateway later)"
echo "Revert: bash $0 --revert"
