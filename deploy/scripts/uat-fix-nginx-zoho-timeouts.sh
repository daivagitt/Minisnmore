#!/usr/bin/env bash
# Ensure uat.minisnmore.com nginx has 600s timeouts for Zoho admin routes (fixes 504 on sync-products).
# Run on UAT VPS as root after updating deploy/nginx/uat.minisnmore.com.locations.conf in the repo.
#
# Usage:
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-fix-nginx-zoho-timeouts.sh
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
REF="${REPO_ROOT}/deploy/nginx/uat.minisnmore.com.locations.conf"

if [ ! -f "$REF" ]; then
  echo "ERROR: missing ${REF}" >&2
  exit 1
fi

NGINX_SITE=""
for candidate in \
  /etc/nginx/sites-enabled/uat.minisnmore.com \
  /etc/nginx/sites-available/uat.minisnmore.com \
  /etc/nginx/conf.d/uat.minisnmore.com.conf; do
  if [ -f "$candidate" ] && grep -q 'uat\.minisnmore\.com' "$candidate" 2>/dev/null; then
    NGINX_SITE="$candidate"
    break
  fi
done

if [ -z "$NGINX_SITE" ]; then
  NGINX_SITE=$(grep -rl 'uat\.minisnmore\.com' /etc/nginx 2>/dev/null | head -1 || true)
fi

if [ -z "$NGINX_SITE" ] || [ ! -f "$NGINX_SITE" ]; then
  echo "ERROR: could not find uat.minisnmore.com nginx config" >&2
  exit 1
fi

echo "Site config: ${NGINX_SITE}"

if grep -q 'location /api/v1/admin/zoho/' "$NGINX_SITE"; then
  echo "OK  Zoho location block already present"
else
  echo "WARN: Zoho location block missing — merge blocks from deploy/nginx/uat.minisnmore.com.locations.conf manually"
  echo "      (proxy_read_timeout 600s for /api/v1/admin/zoho/ and /api/v1/admin/zoho-settings)"
fi

grep -E 'proxy_read_timeout|127\.0\.0\.1:(9180|8080)' "$NGINX_SITE" | head -20 || true

if nginx -t; then
  systemctl reload nginx
  echo "Nginx reloaded."
else
  echo "ERROR: nginx -t failed" >&2
  exit 1
fi

echo ""
echo "Verify (needs admin JWT):"
echo "  bash deploy/scripts/uat-diagnose-zoho-admin.sh"
