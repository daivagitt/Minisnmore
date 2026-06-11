#!/usr/bin/env bash
# One password prompt total (SSH multiplex). Upload shows size + progress.
#
#   bash deploy/scripts/uat-unified-push-from-laptop.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE="${REMOTE:-root@93.127.199.55}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/uat1/mini_ms_test}"
GIT_SHA="$(cd "$ROOT" && git rev-parse --short HEAD 2>/dev/null || echo local)"
BUILD_NUM="${BUILD_NUM:-$(date +%Y%m%d%H%M)}"
REMOTE_TGZ="/tmp/mini-uat-deploy-${BUILD_NUM}.tgz"

echo "=== Building missing JARs (local only, no SSH) ==="
for dir in gateway backend store commerce integration; do
  jar=$(ls -1 "$ROOT/$dir/target/"*.jar 2>/dev/null | head -1 || true)
  if [ -z "$jar" ]; then
    echo "Building $dir..."
    (cd "$ROOT/$dir" && ./mvnw -Pprod -DskipTests clean package -q)
  else
    echo "OK  $dir → $(basename "$jar")"
  fi
done

STAGE="$(mktemp -d "${TMPDIR:-/tmp}/mini-uat-stage.XXXXXX")"
BUNDLE="$(mktemp -t mini-uat-bundle.XXXXXX.tar.gz)"
SSH_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mini-uat-ssh.XXXXXX")"
SSH_SOCK="${SSH_DIR}/socket"

cleanup() {
  ssh -S "$SSH_SOCK" -O exit "$REMOTE" 2>/dev/null || true
  rm -rf "$STAGE" "$SSH_DIR" "$BUNDLE"
}
trap cleanup EXIT

echo "=== Packing release (strip Mac metadata) ==="
export COPYFILE_DISABLE=1
paths=(
  package.json package-lock.json vite.config.ts vitest.config.ts tsconfig.json components.json
  public src deploy/scripts
  gateway/target backend/target store/target commerce/target integration/target
)
for rel in "${paths[@]}"; do
  if [ -e "$ROOT/$rel" ]; then
    mkdir -p "$STAGE/$(dirname "$rel")"
    cp -a "$ROOT/$rel" "$STAGE/$rel"
  fi
done
xattr -cr "$STAGE" 2>/dev/null || true
tar -czf "$BUNDLE" -C "$STAGE" .

if ! tar -tzf "$BUNDLE" | grep -qE 'backend/target/.*\.jar$'; then
  echo "ERROR: backend JAR missing. Run: cd backend && ./mvnw -Pprod -DskipTests package" >&2
  exit 1
fi

BUNDLE_MB="$(du -m "$BUNDLE" | awk '{print $1}')"
echo "Bundle size: ${BUNDLE_MB} MB"

echo "=== SSH login (enter VPS password once) ==="
ssh -M -S "$SSH_SOCK" -o ControlPersist=20m -o ConnectTimeout=30 -f -N "$REMOTE"

echo "=== Uploading to ${REMOTE}:${REMOTE_TGZ} (often 2–8 min for ~${BUNDLE_MB} MB) ==="
scp -o ControlPath="$SSH_SOCK" "$BUNDLE" "${REMOTE}:${REMOTE_TGZ}"

echo "=== Extract + frontend + microservices on VPS ==="
ssh -o ControlPath="$SSH_SOCK" "$REMOTE" "set -euo pipefail
  echo '--- Extracting archive ---'
  mkdir -p '${REMOTE_DIR}'
  cd '${REMOTE_DIR}'
  tar -xzf '${REMOTE_TGZ}' 2>/dev/null || tar -xzf '${REMOTE_TGZ}'
  rm -f '${REMOTE_TGZ}'
  chmod +x deploy/scripts/*.sh
  echo '--- Frontend (npm ci + build + PM2 :3000) ---'
  export REPO_ROOT='${REMOTE_DIR}' BUILD_NUM='${BUILD_NUM}' GIT_SHA='${GIT_SHA}'
  bash deploy/scripts/uat-frontend-pm2.sh
  echo '--- Microservices (PM2 :9180–9184) ---'
  REPO_ROOT='${REMOTE_DIR}' bash deploy/scripts/uat-microservices-isolated-pm2.sh
  echo '--- Deploy finished on server ---'
"

echo ""
echo "Done → ${REMOTE}:${REMOTE_DIR}"
echo "Check: curl -fsS https://uat.minisnmore.com/api/v1/categories | head"
