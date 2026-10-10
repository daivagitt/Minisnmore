#!/usr/bin/env bash
# Build and serve the production storefront/admin UI on :3000.
#
# Usage:
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-frontend-pm2.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  # shellcheck source=/dev/null
  . "$NVM_DIR/nvm.sh"
fi

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"
PM2_NAME="${PM2_NAME:-mini-prod-frontend}"

if [ ! -f "$REPO_ROOT/package.json" ]; then
  echo "ERROR: $REPO_ROOT/package.json missing" >&2
  exit 1
fi

cd "$REPO_ROOT"
if [ -f "$REPO_ROOT/deploy/scripts/ensure-frontend-source.sh" ]; then
  bash "$REPO_ROOT/deploy/scripts/ensure-frontend-source.sh"
fi

# After prod-deploy-frontend-from-laptop.sh: dist/client is pre-built — skip slow VPS npm ci.
if [ "${SKIP_BUILD:-0}" = "1" ] && [ -d dist/client ] && [ -f dist/client/RELEASE.txt ] && [ -f dist/server/server.js ]; then
  echo "SKIP_BUILD=1 — using existing dist/client ($(head -1 dist/client/RELEASE.txt))"
  if ! command -v pm2 >/dev/null 2>&1; then
    echo "WARN: pm2 not installed" >&2
    exit 0
  fi
  pm2 delete mini-uat-frontend >/dev/null 2>&1 || true
  fuser -k "${FRONTEND_PORT}/tcp" >/dev/null 2>&1 || true
  pm2 delete "$PM2_NAME" >/dev/null 2>&1 || true
  pm2 start npm --name "$PM2_NAME" --cwd "$REPO_ROOT" --update-env -- run preview -- --host 127.0.0.1 --port "$FRONTEND_PORT" --strictPort
  pm2 save
  echo "Production frontend PM2 '$PM2_NAME' on http://127.0.0.1:${FRONTEND_PORT} (pre-built dist)"
  exit 0
fi

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-https://minisnmore.com}"
export VITE_SITE_ENV="${VITE_SITE_ENV:-production}"
export VITE_SITE_ORIGIN="${VITE_SITE_ORIGIN:-https://minisnmore.com}"

echo "=== Production frontend build (VITE_API_BASE_URL=$VITE_API_BASE_URL VITE_SITE_ENV=$VITE_SITE_ENV) ==="
echo "Node: $(node -v 2>/dev/null || echo missing)  npm: $(npm -v 2>/dev/null || echo missing)"
echo "Memory: $(free -h 2>/dev/null | awk '/Mem:/ {print $3 "/" $2}' || echo n/a)  $(date -u '+%H:%M:%S UTC')"
export NPM_CONFIG_AUDIT=false
export NPM_CONFIG_FUND=false
npm ci --no-audit --no-fund
ASSET_BACKUP=""
if [ -d dist/client/assets ]; then
  ASSET_BACKUP="$(mktemp -d "${TMPDIR:-/tmp}/mini-client-assets.XXXXXX")"
  cp -a dist/client/assets/. "$ASSET_BACKUP/"
fi
rm -rf dist node_modules/.vite
npm run build
if [ -n "$ASSET_BACKUP" ] && [ -d "$ASSET_BACKUP" ] && [ -d dist/client/assets ]; then
  cp -an "$ASSET_BACKUP/." dist/client/assets/
  rm -rf "$ASSET_BACKUP"
fi
printf '%s\n%s\n' "${BUILD_NUM:-production}" "${GIT_SHA:-local}" > dist/client/RELEASE.txt

if ! command -v pm2 >/dev/null 2>&1; then
  echo "WARN: pm2 not installed; dist/ built but preview not started." >&2
  exit 0
fi

# Remove the legacy name from this production host before binding :3000.
pm2 delete mini-uat-frontend >/dev/null 2>&1 || true
fuser -k "${FRONTEND_PORT}/tcp" >/dev/null 2>&1 || true
pm2 delete "$PM2_NAME" >/dev/null 2>&1 || true
pm2 start npm --name "$PM2_NAME" --cwd "$REPO_ROOT" --update-env -- run preview -- --host 127.0.0.1 --port "$FRONTEND_PORT" --strictPort
pm2 save
echo "Production frontend PM2 '$PM2_NAME' on http://127.0.0.1:${FRONTEND_PORT}"
