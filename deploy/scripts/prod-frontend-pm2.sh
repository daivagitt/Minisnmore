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

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-https://minisnmore.com}"

echo "=== Production frontend build (VITE_API_BASE_URL=$VITE_API_BASE_URL) ==="
export NPM_CONFIG_AUDIT=false NPM_CONFIG_FUND=false
npm ci --no-audit --no-fund
rm -rf dist node_modules/.vite
npm run build
printf '%s\n%s\n' "${BUILD_NUM:-production}" "${GIT_SHA:-local}" > dist/client/RELEASE.txt

if ! command -v pm2 >/dev/null 2>&1; then
  echo "WARN: pm2 not installed; dist/ built but preview not started." >&2
  exit 0
fi

# Remove the legacy UAT name from this production host before binding :3000.
pm2 delete mini-uat-frontend >/dev/null 2>&1 || true
fuser -k "${FRONTEND_PORT}/tcp" >/dev/null 2>&1 || true
pm2 delete "$PM2_NAME" >/dev/null 2>&1 || true
pm2 start npm --name "$PM2_NAME" --cwd "$REPO_ROOT" --update-env -- run preview -- --host 127.0.0.1 --port "$FRONTEND_PORT" --strictPort
pm2 save
echo "Production frontend PM2 '$PM2_NAME' on http://127.0.0.1:${FRONTEND_PORT}"
