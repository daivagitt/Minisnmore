#!/usr/bin/env bash
# Sync sources needed for VPS CI (mvn test + jenkins scripts). JAR-only deploy is not enough.
#
#   bash deploy/scripts/prod-sync-ci-sources-from-laptop.sh
#
# Then on VPS (or via prod-run-ci-reports-from-laptop.sh):
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-run-ci-reports-on-server.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE="${REMOTE:-root@93.127.199.55}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/uat1/mini_ms_test}"

SSH_CTRL="$(mktemp -d "${TMPDIR:-/tmp}/mini-ci-sync-ssh.XXXXXX")"
SSH_SOCK="${SSH_CTRL}/socket"
cleanup() {
  ssh -S "$SSH_SOCK" -O exit "$REMOTE" 2>/dev/null || true
  rm -rf "$SSH_CTRL"
}
trap cleanup EXIT

echo "=== SSH (one password) ==="
ssh -M -S "$SSH_SOCK" -o ControlPersist=20m -f -N "$REMOTE"
ssh -S "$SSH_SOCK" "$REMOTE" "mkdir -p ${REMOTE_DIR}"

rsync_uat() {
  rsync -az -e "ssh -S $SSH_SOCK" "$@"
}

echo "=== Syncing frontend + Java sources + jenkins scripts ==="
rsync_uat \
  --exclude node_modules \
  --exclude '**/target' \
  --exclude .git \
  --exclude dist \
  --exclude artifacts \
  --exclude coverage \
  "$ROOT/package.json" "$ROOT/package-lock.json" "$ROOT/vite.config.ts" \
  "$ROOT/vitest.config.ts" "$ROOT/tsconfig.json" "$ROOT/components.json" \
  "$ROOT/src" \
  "$REMOTE:${REMOTE_DIR}/"

for mod in gateway backend store commerce integration; do
  echo "  $mod/"
  rsync_uat \
    --exclude target \
    "$ROOT/$mod/" "$REMOTE:${REMOTE_DIR}/$mod/"
done

echo "  jenkins/"
rsync_uat "$ROOT/jenkins/" "$REMOTE:${REMOTE_DIR}/jenkins/"
rsync_uat "$ROOT/deploy/scripts/" "$REMOTE:${REMOTE_DIR}/deploy/scripts/"

echo ""
echo "Done. On VPS run:"
echo "  REPO_ROOT=${REMOTE_DIR} bash ${REMOTE_DIR}/deploy/scripts/prod-run-ci-reports-on-server.sh"
