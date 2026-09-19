#!/usr/bin/env bash
# Build gateway JAR locally, upload to UAT, fix env/nginx, restart — fixes 504 + route crash loop.
#
#   bash deploy/scripts/prod-deploy-gateway-from-laptop.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE="${REMOTE:-root@93.127.199.55}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/uat1/mini_ms_test}"

SSH_CTRL="$(mktemp -d "${TMPDIR:-/tmp}/mini-gw-deploy-ssh.XXXXXX")"
SSH_SOCK="${SSH_CTRL}/socket"
cleanup() {
  ssh -S "$SSH_SOCK" -O exit "$REMOTE" 2>/dev/null || true
  rm -rf "$SSH_CTRL"
}
trap cleanup EXIT

echo "=== Build gateway + integration JARs ==="
cd "$ROOT/gateway"
./mvnw -q -Pprod -DskipTests -Djacoco.skip=true clean package
GW_JAR="$(ls -1 "$ROOT/gateway/target"/gateway-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"

cd "$ROOT/integration"
./mvnw -q -DskipTests -Djacoco.skip=true clean package
INT_JAR="$(ls -1 "$ROOT/integration/target"/miniandmore-integration-*.jar 2>/dev/null | grep -v '\.original$' | head -1)"

if [ ! -f "$GW_JAR" ] || [ ! -f "$INT_JAR" ]; then
  echo "ERROR: JAR build failed" >&2
  exit 1
fi
echo "OK  gateway: $GW_JAR"
echo "OK  integration: $INT_JAR"

echo "=== SSH ==="
ssh -M -S "$SSH_SOCK" -o ControlPersist=30m -f -N "$REMOTE"

echo "=== Upload gateway JAR + deploy scripts ==="
ssh -S "$SSH_SOCK" "$REMOTE" "mkdir -p ${REMOTE_DIR}/deploy ${REMOTE_DIR}/deploy/scripts/lib ${REMOTE_DIR}/integration/target ${REMOTE_DIR}/deploy/nginx"
rsync -az -e "ssh -S $SSH_SOCK" "$GW_JAR" "${REMOTE}:${REMOTE_DIR}/deploy/gateway-prod.jar"
rsync -az -e "ssh -S $SSH_SOCK" "$INT_JAR" "${REMOTE}:${REMOTE_DIR}/integration/target/"
rsync -az -e "ssh -S $SSH_SOCK" \
  "$ROOT/deploy/scripts/" "${REMOTE}:${REMOTE_DIR}/deploy/scripts/"
rsync -az -e "ssh -S $SSH_SOCK" \
  "$ROOT/deploy/nginx/minisnmore.com.locations.conf" \
  "${REMOTE}:${REMOTE_DIR}/deploy/nginx/minisnmore.com.locations.conf"

echo "=== Fix uat.env, nginx Zoho timeouts, restart gateway ==="
ssh -S "$SSH_SOCK" "$REMOTE" "set -euo pipefail
  export PATH='/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:'\$PATH
  REPO='${REMOTE_DIR}'
  chmod +x \"\$REPO/deploy/scripts/\"*.sh \"\$REPO/deploy/scripts/lib/\"*.sh 2>/dev/null || true

  if [ -f /etc/mini-and-more/prod.env ]; then
    if grep -qE '^SPRING_CLOUD_GATEWAY.*ROUTES' /etc/mini-and-more/prod.env; then
      cp -a /etc/mini-and-more/prod.env /etc/mini-and-more/prod.env.bak-\$(date +%Y%m%d%H%M)
      sed -i '/^SPRING_CLOUD_GATEWAY.*ROUTES/d' /etc/mini-and-more/prod.env
      echo 'Removed SPRING_CLOUD_GATEWAY route lines from prod.env'
    fi
  fi

  bash \"\$REPO/deploy/scripts/prod-apply-nginx-zoho-timeouts.sh\" || true
  bash \"\$REPO/deploy/scripts/prod-restart-isolated-gateway.sh\"
  bash \"\$REPO/deploy/scripts/prod-verify-gateway-stack.sh\"
"

echo ""
echo "=== Verify from laptop ==="
sleep 5
curl -fsS -m 20 "https://minisnmore.com/api/v1/categories" | head -c 80 && echo ""
echo "Done. Test Zoho admin: https://minisnmore.com/admin/zoho"
