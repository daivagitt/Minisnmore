#!/usr/bin/env bash
# Restart production storefront preview after dist/ already built (no npm ci).
# Use after manual `npm run build` so HTML and hashed /assets stay in sync.
#
# Usage:
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-frontend-restart.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  # shellcheck source=/dev/null
  . "$NVM_DIR/nvm.sh"
fi

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
PM2_NAME="${PM2_NAME:-mini-prod-frontend}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"

if [ ! -f "$REPO_ROOT/dist/server/server.js" ] || [ ! -d "$REPO_ROOT/dist/client/assets" ]; then
  echo "ERROR: dist/ missing — run deploy/scripts/prod-frontend-pm2.sh first." >&2
  exit 1
fi

cd "$REPO_ROOT"
# Delete every process with this name (duplicate ids can linger after a failed restart).
pm2 delete "$PM2_NAME" >/dev/null 2>&1 || true
while pm2 jlist 2>/dev/null | grep -q "\"name\":\"$PM2_NAME\""; do
  pm2 delete "$PM2_NAME" >/dev/null 2>&1 || true
  sleep 0.2
done
fuser -k "${FRONTEND_PORT}/tcp" >/dev/null 2>&1 || true
sleep 0.3
pm2 start npm --name "$PM2_NAME" --cwd "$REPO_ROOT" --update-env -- run preview -- --host 127.0.0.1 --port "$FRONTEND_PORT" --strictPort
pm2 save

echo "OK  restarted $PM2_NAME (preview reads fresh dist/)"
