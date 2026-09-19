#!/usr/bin/env bash
# Find which nginx file serves minisnmore.com and show API proxy_pass ports.
# Run on VPS: bash deploy/scripts/prod-find-nginx-config.sh
set -euo pipefail

echo "=== Files mentioning minisnmore.com ==="
grep -rl 'minisnmore\.com' /etc/nginx 2>/dev/null || echo "(none)"

echo ""
echo "=== proxy_pass for /api/ (all nginx configs) ==="
grep -rn 'location /api/' /etc/nginx 2>/dev/null || true
grep -rn 'proxy_pass.*api' /etc/nginx 2>/dev/null | head -20 || true

echo ""
echo "=== proxy_pass 8080 / 9180 / 8081 / 3000 ==="
grep -rnE '127\.0\.0\.1:(8080|9180|8081|3000)' /etc/nginx 2>/dev/null || echo "(none)"

echo ""
echo "=== sites-enabled ==="
ls -la /etc/nginx/sites-enabled/ 2>/dev/null || true

echo ""
echo "=== nginx -T (server_name minisnmore.com) ==="
nginx -T 2>/dev/null | grep -A2 'server_name.*minisnmore' | head -40
