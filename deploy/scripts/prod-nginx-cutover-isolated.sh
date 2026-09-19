#!/usr/bin/env bash
# Point minisnmore.com API/OAuth from monolith gateway (:8080) to isolated MS gateway (:9180).
# Does NOT stop a legacy monolith on :8081 — only changes what Nginx proxies for /api/, /oauth2/, etc.
#
# Usage (on production VPS as root):
#   ISOLATED_GATEWAY_PORT=9180 bash deploy/scripts/prod-nginx-cutover-isolated.sh
# Rollback:
#   LEGACY_GATEWAY_PORT=8080 bash deploy/scripts/prod-nginx-cutover-isolated.sh --rollback
set -euo pipefail

ISOLATED_GATEWAY_PORT="${ISOLATED_GATEWAY_PORT:-9180}"
LEGACY_GATEWAY_PORT="${LEGACY_GATEWAY_PORT:-8080}"
ROLLBACK=0
if [ "${1:-}" = "--rollback" ]; then
  ROLLBACK=1
fi

TARGET_PORT="$ISOLATED_GATEWAY_PORT"
FROM_PORT="$LEGACY_GATEWAY_PORT"
if [ "$ROLLBACK" -eq 1 ]; then
  TARGET_PORT="$LEGACY_GATEWAY_PORT"
  FROM_PORT="$ISOLATED_GATEWAY_PORT"
  echo "Rolling back Nginx to gateway :${TARGET_PORT}"
else
  echo "Cutting over Nginx to isolated gateway :${TARGET_PORT}"
fi

if ! curl -fsS -m 10 "http://127.0.0.1:${ISOLATED_GATEWAY_PORT}/management/health/liveness" >/dev/null 2>&1; then
  echo "ERROR: Isolated gateway not healthy on :${ISOLATED_GATEWAY_PORT}. Fix stack before cutover." >&2
  exit 1
fi

NGINX_SITE=""
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/nginx-prod-site.sh
source "${SCRIPT_DIR}/lib/nginx-prod-site.sh"
NGINX_SITE="$(find_prod_nginx_site || true)"

if [ -z "$NGINX_SITE" ] || [ ! -f "$NGINX_SITE" ]; then
  echo "ERROR: Could not find minisnmore.com nginx config under /etc/nginx." >&2
  echo "Manually set proxy_pass to http://127.0.0.1:${TARGET_PORT} for /api/, /oauth2/, /login/oauth2/, /v3/api-docs, /swagger-ui/, /management/" >&2
  echo "See deploy/nginx/prod.minisnmore.com.isolated-9180.locations.conf" >&2
  exit 1
fi

echo "Config file: $NGINX_SITE"
BACKUP="${NGINX_SITE}.bak-isolated-$(date +%Y%m%d-%H%M%S)"
cp -a "$NGINX_SITE" "$BACKUP"
echo "Backup: $BACKUP"

sed -i "s|127\.0\.0\.1:${FROM_PORT}|127.0.0.1:${TARGET_PORT}|g" "$NGINX_SITE"

if ! nginx -t; then
  echo "ERROR: nginx -t failed — restoring backup" >&2
  cp -a "$BACKUP" "$NGINX_SITE"
  nginx -t
  exit 1
fi

systemctl reload nginx
echo "Nginx reloaded. Public API now → :${TARGET_PORT}"
echo ""
echo "Verify:"
echo "  curl -fsS https://minisnmore.com/management/health/liveness"
echo "  curl -fsS https://minisnmore.com/api/v1/categories | head -c 120"
echo ""
echo "Rollback: LEGACY_GATEWAY_PORT=${LEGACY_GATEWAY_PORT} bash $0 --rollback"
