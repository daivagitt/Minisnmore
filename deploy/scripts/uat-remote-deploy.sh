#!/usr/bin/env bash
# Runs on the UAT VPS via: ssh host RELEASE_DIR=... GIT_SHA=... BUILD_NUM=... bash -s < uat-remote-deploy.sh
# API traffic is served by the gateway (8080); start Java services with deploy/scripts/uat-microservices-pm2.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
export NVM_DIR="${NVM_DIR:-$HOME/.nvm}"
if [ -s "$NVM_DIR/nvm.sh" ]; then
  # shellcheck source=/dev/null
  . "$NVM_DIR/nvm.sh"
fi

: "${RELEASE_DIR:?RELEASE_DIR is required}"
: "${CURRENT_LINK:?CURRENT_LINK is required}"
: "${GIT_SHA:?GIT_SHA is required}"
: "${BUILD_NUM:?BUILD_NUM is required}"

cd "$RELEASE_DIR"
npm ci

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

ASSET_BACKUP=""
if [ -d "${CURRENT_LINK}/dist/client/assets" ]; then
  ASSET_BACKUP="$(mktemp -d "${TMPDIR:-/tmp}/mini-client-assets.XXXXXX")"
  cp -a "${CURRENT_LINK}/dist/client/assets/." "$ASSET_BACKUP/"
fi

rm -rf dist node_modules/.vite
export VITE_API_BASE_URL="${VITE_API_BASE_URL:-https://uat.minisnmore.com}"
export VITE_SITE_ENV="${VITE_SITE_ENV:-uat}"
export VITE_SITE_ORIGIN="${VITE_SITE_ORIGIN:-https://minisnmore.com}"
npm run build

if [ -n "$ASSET_BACKUP" ] && [ -d "$ASSET_BACKUP" ] && [ -d dist/client/assets ]; then
  cp -an "$ASSET_BACKUP/." dist/client/assets/
  rm -rf "$ASSET_BACKUP"
fi

printf '%s\n%s\n' "$BUILD_NUM" "$GIT_SHA" > dist/client/RELEASE.txt
if [ "$VITE_SITE_ENV" = "uat" ] && [ -f public/robots.uat.txt ]; then
  cp public/robots.uat.txt dist/client/robots.txt
fi

ln -sfn "$RELEASE_DIR" "$CURRENT_LINK"

if command -v pm2 >/dev/null 2>&1; then
  cd "$CURRENT_LINK"
  fuser -k 3000/tcp >/dev/null 2>&1 || true
  pm2 delete mini-uat-frontend >/dev/null 2>&1 || true
  pm2 start npm --name mini-uat-frontend -- run preview -- --host 127.0.0.1 --port 3000 --strictPort
  pm2 save

  for attempt in $(seq 1 12); do
    sleep 5
    if curl -fsS http://127.0.0.1:3000/RELEASE.txt 2>/dev/null | head -1 | grep -q "^${BUILD_NUM}$"; then
      echo "Frontend release marker OK (build ${BUILD_NUM}, commit ${GIT_SHA})."
      if curl -fsS http://127.0.0.1:3000/ | grep -qi 'softness made for first cuddles'; then
        echo 'ERROR: Frontend still serves template hero copy. Check git branch and npm run build output.' >&2
        exit 1
      fi
      echo 'Frontend is ready on port 3000.'
      if [ -f "$RELEASE_DIR/deploy/scripts/uat-microservices-pm2.sh" ]; then
        chmod +x "$RELEASE_DIR/deploy/scripts/uat-microservices-pm2.sh"
        REPO_ROOT="$RELEASE_DIR" bash "$RELEASE_DIR/deploy/scripts/uat-microservices-pm2.sh"
      else
        echo 'WARN: uat-microservices-pm2.sh not found; start gateway :8080, store :8081, commerce :8082, integration :8083, core :8084 manually.'
      fi
      exit 0
    fi
    echo "Waiting for frontend readiness (${attempt}/12)..."
  done
  echo 'Frontend did not become ready on port 3000 within ~1 minute.' >&2
  pm2 logs mini-uat-frontend --nostream --lines 40 >&2 || true
  exit 1
else
  echo 'pm2 is not installed on remote host; release copied but services not restarted.'
fi
