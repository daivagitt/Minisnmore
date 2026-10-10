#!/usr/bin/env bash
# Point uat.minisnmore.com API/OAuth from monolith gateway (:8080) to isolated MS gateway (:9180).
# Does NOT stop mini-uat monolith on :8081 — only changes what Nginx proxies for /api/, /oauth2/, etc.
#
# Usage (on UAT VPS as root):
#   ISOLATED_GATEWAY_PORT=9180 bash deploy/scripts/uat-nginx-cutover-isolated.sh
# Rollback:
#   LEGACY_GATEWAY_PORT=8080 bash deploy/scripts/uat-nginx-cutover-isolated.sh --rollback
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
for candidate in \
  /etc/nginx/sites-enabled/uat.minisnmore.com \
  /etc/nginx/sites-available/uat.minisnmore.com \
  /etc/nginx/sites-enabled/default \
  /etc/nginx/sites-enabled/minisnmore.com \
  /etc/nginx/sites-enabled/uat.minisnmore.com.conf \
  /etc/nginx/conf.d/uat.minisnmore.com.conf \
  /etc/nginx/conf.d/minisnmore.conf \
  /etc/nginx/conf.d/uat.conf; do
  if [ -f "$candidate" ] && grep -qE 'uat\.minisnmore\.com|127\.0\.0\.1:(8080|9180|8081)' "$candidate" 2>/dev/null; then
    NGINX_SITE="$candidate"
    break
  fi
done

if [ -z "$NGINX_SITE" ]; then
  NGINX_SITE=$(grep -rl 'uat\.minisnmore\.com' /etc/nginx 2>/dev/null | head -1 || true)
fi

if [ -z "$NGINX_SITE" ]; then
  # Hostinger / custom layouts: active config from nginx -T
  NGINX_SITE=$(nginx -T 2>/dev/null | awk '
    /# configuration file / { f=$4; sub(/:$/,"",f) }
    /server_name.*uat\.minisnmore\.com/ { if (f!="") { print f; exit } }
  ' || true)
fi

if [ -z "$NGINX_SITE" ] || [ ! -f "$NGINX_SITE" ]; then
  echo "ERROR: Could not find uat.minisnmore.com nginx config under /etc/nginx." >&2
  echo "Manually set proxy_pass to http://127.0.0.1:${TARGET_PORT} for /api/, /oauth2/, /login/oauth2/, /v3/api-docs, /swagger-ui/, /management/" >&2
  echo "See deploy/nginx/uat.minisnmore.com.locations.conf" >&2
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
echo "  curl -fsS https://uat.minisnmore.com/management/health/liveness"
echo "  curl -fsS https://uat.minisnmore.com/api/v1/categories | head -c 120"
echo ""
echo "Rollback: LEGACY_GATEWAY_PORT=${LEGACY_GATEWAY_PORT} bash $0 --rollback"
