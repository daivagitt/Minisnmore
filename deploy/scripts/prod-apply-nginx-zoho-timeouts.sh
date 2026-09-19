#!/usr/bin/env bash
# Insert / refresh Zoho admin nginx blocks (600s timeout) on minisnmore.com.
# Run on VPS as root:
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-apply-nginx-zoho-timeouts.sh
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
REF="${REPO_ROOT}/deploy/nginx/prod.minisnmore.com.isolated-9180.locations.conf"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/nginx-prod-site.sh
source "${SCRIPT_DIR}/lib/nginx-prod-site.sh"

NGINX_SITE="$(find_prod_nginx_site || true)"
if [ -z "$NGINX_SITE" ] || [ ! -f "$NGINX_SITE" ]; then
  echo "ERROR: could not find live minisnmore.com nginx config" >&2
  exit 1
fi
if [[ "$NGINX_SITE" == *".bak"* ]]; then
  echo "ERROR: refused to edit backup file: ${NGINX_SITE}" >&2
  exit 1
fi

echo "Nginx site (live): ${NGINX_SITE}"
BACKUP="${NGINX_SITE}.bak-zoho-$(date +%Y%m%d-%H%M%S)"
cp -a "$NGINX_SITE" "$BACKUP"
echo "Backup: ${BACKUP}"

sed -i "s|127\.0\.0\.1:8080/api/|127.0.0.1:${GATEWAY_PORT}/api/|g" "$NGINX_SITE"
sed -i "s|127\.0\.0\.1:8080/oauth2/|127.0.0.1:${GATEWAY_PORT}/oauth2/|g" "$NGINX_SITE"
sed -i "s|127\.0\.0\.1:8080/login/oauth2/|127.0.0.1:${GATEWAY_PORT}/login/oauth2/|g" "$NGINX_SITE"

export GATEWAY_PORT NGINX_SITE REF
python3 <<'PY'
import os, re
from pathlib import Path

site_path = Path(os.environ["NGINX_SITE"])
ref_path = Path(os.environ["REF"])
port = os.environ["GATEWAY_PORT"]
text = site_path.read_text()

zoho_block = f"""    # Zoho admin — long-running sync (auto-managed)
    location /api/v1/admin/zoho/ {{
        proxy_pass http://127.0.0.1:{port}/api/v1/admin/zoho/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
        proxy_connect_timeout 10s;
        proxy_send_timeout 600s;
        proxy_read_timeout 600s;
    }}

    location /api/v1/admin/zoho-settings {{
        proxy_pass http://127.0.0.1:{port}/api/v1/admin/zoho-settings;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
        proxy_connect_timeout 10s;
        proxy_send_timeout 600s;
        proxy_read_timeout 600s;
    }}

"""

if "location /api/v1/admin/zoho/" not in text:
    if ref_path.is_file():
        ref = ref_path.read_text()
        start = ref.find("    # Zoho admin")
        end = ref.find("    location /api/ {")
        if start >= 0 and end > start:
            zoho_block = ref[start:end].replace("9180", port)
    marker = "    location /api/ {"
    if marker not in text:
        raise SystemExit("Could not find location /api/ { in nginx site")
    text = text.replace(marker, zoho_block + marker, 1)
    print("Inserted Zoho location blocks")
else:
    def patch_block(content: str, loc: str) -> str:
        pattern = rf"(location {re.escape(loc)} \{{.*?\n    \}}\n)"
        m = re.search(pattern, content, flags=re.S)
        if not m:
            raise SystemExit(f"Could not parse nginx block: {loc}")
        block = m.group(1)
        block = re.sub(r"proxy_read_timeout [^;]+;", "proxy_read_timeout 600s;", block)
        block = re.sub(r"proxy_send_timeout [^;]+;", "proxy_send_timeout 600s;", block)
        if "proxy_read_timeout" not in block:
            block = block.replace("proxy_connect_timeout 10s;", "proxy_connect_timeout 10s;\n        proxy_send_timeout 600s;\n        proxy_read_timeout 600s;")
        return content.replace(m.group(1), block, 1)

    text = patch_block(text, "/api/v1/admin/zoho/")
    text = patch_block(text, "/api/v1/admin/zoho-settings")
    print("Updated Zoho blocks to 600s timeouts")

site_path.write_text(text)
PY

nginx -t
systemctl reload nginx
echo "Nginx reloaded (live site: ${NGINX_SITE})"
