#!/usr/bin/env bash
# Run on production VPS to find why the site shows an old frontend.
#   bash deploy/scripts/prod-diagnose-frontend.sh
set -euo pipefail

REPO="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
LEGACY="/var/www/uat1/mini_and_more/current"
PORT="${FRONTEND_PORT:-3000}"

echo "=== PM2 mini-prod-frontend ==="
pm2 describe mini-prod-frontend 2>/dev/null | grep -E 'status|cwd|script path|exec cwd' || echo "NOT RUNNING"

echo ""
echo "=== Port :${PORT} ==="
ss -tlnp | grep ":${PORT} " || echo "nothing listening on :${PORT}"

echo ""
echo "=== dist in ${REPO} ==="
if [ -f "${REPO}/dist/client/RELEASE.txt" ]; then
  echo "RELEASE: $(cat "${REPO}/dist/client/RELEASE.txt" | tr '\n' ' ')"
  grep -rl WELCOME10 "${REPO}/dist/client" 2>/dev/null | head -2 || echo "WELCOME10: MISSING in dist"
  grep -rl 'Letters from us' "${REPO}/dist/client" 2>/dev/null | head -1 || echo "new footer: MISSING"
else
  echo "NO dist/client/RELEASE.txt — frontend never built in ${REPO}"
fi

echo ""
echo "=== Legacy path ${LEGACY} ==="
if [ -d "${LEGACY}/dist/client" ]; then
  echo "Legacy RELEASE: $(cat "${LEGACY}/dist/client/RELEASE.txt" 2>/dev/null | tr '\n' ' ' || echo n/a)"
  _cwd=$(pm2 jlist 2>/dev/null | python3 -c "
import json,sys
for p in json.load(sys.stdin):
    if p.get('name')=='mini-prod-frontend':
        print(p.get('pm2_env',{}).get('pm_cwd',''))
        break
" 2>/dev/null || true)
  if [[ "$_cwd" == *mini_and_more* ]]; then
    echo "PROBLEM: PM2 cwd is legacy mini_and_more — run deploy from mini_ms_test"
  fi
fi

echo ""
echo "=== curl localhost:${PORT} ==="
curl -fsS -m 8 "http://127.0.0.1:${PORT}/RELEASE.txt" 2>/dev/null || echo "RELEASE.txt: FAIL"
curl -fsS -m 8 "http://127.0.0.1:${PORT}/" 2>/dev/null | grep -oE 'WELCOME10|Letters from us' | sort -u || echo "homepage markers: none"

echo ""
echo "=== nginx site root ==="
for f in /etc/nginx/sites-enabled/*; do
  if grep -q minisnmore.com "$f" 2>/dev/null; then
    echo "file: $f"
    grep -E 'proxy_pass.*3000|proxy_pass.*808' "$f" || true
  fi
done

echo ""
echo "Fix: bash deploy/scripts/prod-deploy-frontend-from-laptop.sh  (from Mac)"
echo "  or on VPS: SKIP_BUILD=1 REPO_ROOT=${REPO} bash deploy/scripts/prod-frontend-pm2.sh"
