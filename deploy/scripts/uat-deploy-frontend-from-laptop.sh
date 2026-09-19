#!/usr/bin/env bash
# Build storefront on your Mac, rsync dist to UAT, restart PM2 (fast + reliable).
#
#   bash deploy/scripts/uat-deploy-frontend-from-laptop.sh
#
# Verify:
#   curl -fsS https://uat.minisnmore.com/RELEASE.txt
#   curl -fsS https://uat.minisnmore.com/ | grep -o WELCOME10 | head -1
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE="${REMOTE:-root@93.127.199.55}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/uat1/mini_ms_test}"
GIT_SHA="$(cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null || echo local)"
BUILD_NUM="${BUILD_NUM:-$(date +%Y%m%d%H%M)}"
PM2_NAME="${PM2_NAME:-mini-uat-frontend}"
FRONTEND_PORT="${FRONTEND_PORT:-3000}"

SSH_CTRL="$(mktemp -d "${TMPDIR:-/tmp}/mini-fe-deploy-ssh.XXXXXX")"
SSH_SOCK="${SSH_CTRL}/socket"
cleanup() {
  ssh -S "$SSH_SOCK" -O exit "$REMOTE" 2>/dev/null || true
  rm -rf "$SSH_CTRL"
}
trap cleanup EXIT

echo "=== Build locally (VITE_API_BASE_URL=https://uat.minisnmore.com) ==="
cd "$ROOT"
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-https://uat.minisnmore.com}"
export VITE_SITE_ENV="${VITE_SITE_ENV:-uat}"
export VITE_SITE_ORIGIN="${VITE_SITE_ORIGIN:-https://minisnmore.com}"
rm -rf dist
npm run build
if [ ! -d dist/client ]; then
  echo "ERROR: dist/client missing after build" >&2
  exit 1
fi
printf '%s\n%s\n' "$BUILD_NUM" "$GIT_SHA" > dist/client/RELEASE.txt
if [ "$VITE_SITE_ENV" = "uat" ] && [ -f public/robots.uat.txt ]; then
  cp public/robots.uat.txt dist/client/robots.txt
fi
if ! grep -rq 'WELCOME10' dist/client; then
  echo "ERROR: WELCOME10 not in dist/client — wrong branch or build?" >&2
  exit 1
fi
echo "OK  dist built (${BUILD_NUM} ${GIT_SHA})"

echo "=== SSH (one password) ==="
ssh -M -S "$SSH_SOCK" -o ControlPersist=30m -f -N "$REMOTE"
ssh -S "$SSH_SOCK" "$REMOTE" "mkdir -p ${REMOTE_DIR}/dist ${REMOTE_DIR}/deploy/scripts"

echo "=== Upload dist/ (client + server for TanStack preview) ==="
# Do not delete old hashed client assets during deploy. Browsers may still have
# cached HTML/route chunks that reference the previous build's /assets files.
rsync -az -e "ssh -S $SSH_SOCK" \
  "$ROOT/dist/" "${REMOTE}:${REMOTE_DIR}/dist/"

rsync -az -e "ssh -S $SSH_SOCK" \
  "$ROOT/package.json" "$ROOT/package-lock.json" \
  "$REMOTE:${REMOTE_DIR}/"

rsync -az -e "ssh -S $SSH_SOCK" \
  "$ROOT/deploy/scripts/uat-frontend-pm2.sh" \
  "$REMOTE:${REMOTE_DIR}/deploy/scripts/"

echo "=== Restart PM2 preview (no npm ci on VPS) ==="
ssh -S "$SSH_SOCK" "$REMOTE" "set -euo pipefail
  export PATH=\"/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:\$PATH\"
  export NVM_DIR=\"\${NVM_DIR:-\$HOME/.nvm}\"
  [ -s \"\$NVM_DIR/nvm.sh\" ] && . \"\$NVM_DIR/nvm.sh\"
  REPO='${REMOTE_DIR}'
  cd \"\$REPO\"
  if [ ! -f package.json ]; then
    echo 'ERROR: missing package.json in '\$REPO >&2
    exit 1
  fi
  chmod +x deploy/scripts/uat-frontend-pm2.sh
  # Only free the port if listeners look like this frontend (PM2/npm preview), not unrelated services.
  if command -v fuser >/dev/null 2>&1; then
    for pid in \$(fuser ${FRONTEND_PORT}/tcp 2>/dev/null | tr -s ' ' '\\n' | grep -E '^[0-9]+\$' || true); do
      cmd=\$(ps -p \"\$pid\" -o cmd= 2>/dev/null || true)
      if echo \"\$cmd\" | grep -qE '${PM2_NAME}|npm.*preview|node.*preview'; then
        echo \"Stopping pid \$pid (\$cmd)\"
        kill \"\$pid\" 2>/dev/null || true
      else
        echo \"WARN: port ${FRONTEND_PORT} in use by pid \$pid — not our frontend; leaving it alone: \$cmd\" >&2
      fi
    done
  fi
  sleep 1
  pm2 delete ${PM2_NAME} >/dev/null 2>&1 || true
  pm2 start npm --name ${PM2_NAME} --cwd \"\$REPO\" --update-env -- run preview -- --host 127.0.0.1 --port ${FRONTEND_PORT} --strictPort
  pm2 save
  sleep 3
  echo '--- PM2 cwd ---'
  pm2 describe ${PM2_NAME} 2>/dev/null | grep -E 'cwd|status|script' || pm2 list
  echo '--- Local verify ---'
  head -1 \"\$REPO/dist/client/RELEASE.txt\" || true
  curl -fsS -m 10 http://127.0.0.1:${FRONTEND_PORT}/RELEASE.txt || echo 'WARN: :${FRONTEND_PORT} RELEASE.txt not reachable yet'
  grep -rl WELCOME10 \"\$REPO/dist/client\" | head -2 || { echo 'ERROR: WELCOME10 missing on VPS dist' >&2; exit 1; }
"

echo ""
echo "Done. Hard-refresh https://uat.minisnmore.com (Cmd+Shift+R)."
echo "Release: https://uat.minisnmore.com/RELEASE.txt → expect first line: ${BUILD_NUM}"
