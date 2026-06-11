#!/usr/bin/env bash
# Build integration JAR locally, upload to UAT, restart mini-uat-ms-integration only.
#
#   bash deploy/scripts/uat-deploy-integration-from-laptop.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE="${REMOTE:-root@93.127.199.55}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/uat1/mini_ms_test}"

SSH_CTRL="$(mktemp -d "${TMPDIR:-/tmp}/mini-int-deploy-ssh.XXXXXX")"
SSH_SOCK="${SSH_CTRL}/socket"
cleanup() {
  ssh -S "$SSH_SOCK" -O exit "$REMOTE" 2>/dev/null || true
  rm -rf "$SSH_CTRL"
}
trap cleanup EXIT

echo "=== Build integration JAR ==="
cd "$ROOT/integration"
./mvnw -q -DskipTests -Djacoco.skip=true clean package
INT_JAR="$(ls -1 "$ROOT/integration/target"/miniandmore-integration-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"
if [ ! -f "$INT_JAR" ]; then
  echo "ERROR: integration JAR build failed" >&2
  exit 1
fi
echo "OK  $INT_JAR"

echo "=== SSH ==="
ssh -M -S "$SSH_SOCK" -o ControlPersist=30m -f -N "$REMOTE"

echo "=== Upload integration JAR + restart scripts ==="
ssh -S "$SSH_SOCK" "$REMOTE" "mkdir -p ${REMOTE_DIR}/integration/target ${REMOTE_DIR}/deploy/scripts/lib"
rsync -az -e "ssh -S $SSH_SOCK" "$INT_JAR" "${REMOTE}:${REMOTE_DIR}/integration/target/"
rsync -az -e "ssh -S $SSH_SOCK" \
  "$ROOT/deploy/scripts/uat-restart-isolated-integration.sh" \
  "$ROOT/deploy/scripts/uat-diagnose-zoho-admin.sh" \
  "$ROOT/deploy/scripts/uat-verify-gateway-stack.sh" \
  "${REMOTE}:${REMOTE_DIR}/deploy/scripts/"

echo "=== Restart integration on :9183 ==="
ssh -S "$SSH_SOCK" "$REMOTE" "set -euo pipefail
  export PATH='/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:'\$PATH
  chmod +x ${REMOTE_DIR}/deploy/scripts/uat-restart-isolated-integration.sh
  REPO_ROOT=${REMOTE_DIR} bash ${REMOTE_DIR}/deploy/scripts/uat-restart-isolated-integration.sh
  echo ''
  echo '=== Zoho admin smoke (integration direct) ==='
  UAT_ADMIN_EMAIL=\${UAT_ADMIN_EMAIL:-admin@uat.minisnmore.com} \
  UAT_ADMIN_PASSWORD=\${UAT_ADMIN_PASSWORD:-admin6} \
  bash ${REMOTE_DIR}/deploy/scripts/uat-diagnose-zoho-admin.sh
"

echo ""
echo "Done. Admin Zoho: https://uat.minisnmore.com/admin/zoho"
echo "New endpoints: GET /api/v1/admin/zoho/product-sync-status, PUT /api/v1/admin/zoho-settings"
