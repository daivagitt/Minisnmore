#!/usr/bin/env bash
# Push microservice JARs to production and start isolated stack (9180–9184).
# Does NOT touch the monolith on :8081 or Nginx routing to it.
#
# Usage (from repo root on your laptop):
#   bash deploy/scripts/uat-isolated-push-from-laptop.sh
#
# Requires: rsync, ssh access to root@93.127.199.55 (password or key).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE="${REMOTE:-root@93.127.199.55}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/uat1/mini_ms_test}"

VERIFY_JAR="$ROOT/deploy/scripts/verify-spring-boot-fat-jar.sh"

echo "=== Building missing JARs (skip if up to date) ==="
for dir in gateway backend store commerce integration; do
  jar=$(ls -1 "$ROOT/$dir/target/"*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)
  if [ -z "$jar" ]; then
    echo "Building $dir..."
    (cd "$ROOT/$dir" && ./mvnw -Pprod -DskipTests clean package -q)
    jar=$(ls -1 "$ROOT/$dir/target/"*.jar 2>/dev/null | grep -v '\.original$' | head -1 || true)
  else
    echo "OK  $dir → $(basename "$jar")"
  fi
  if [ -n "$jar" ] && [ -x "$VERIFY_JAR" ]; then
    bash "$VERIFY_JAR" "$jar" "$dir"
  fi
done

echo ""
echo "=== Syncing JARs + deploy scripts to ${REMOTE}:${REMOTE_DIR} ==="
ssh "$REMOTE" "mkdir -p \
  ${REMOTE_DIR}/gateway/target \
  ${REMOTE_DIR}/backend/target \
  ${REMOTE_DIR}/store/target \
  ${REMOTE_DIR}/commerce/target \
  ${REMOTE_DIR}/integration/target \
  ${REMOTE_DIR}/deploy/scripts"

GATEWAY_FAT="$ROOT/gateway/target/gateway-0.0.1-SNAPSHOT.jar"
bash "$VERIFY_JAR" "$GATEWAY_FAT" "gateway"

mkdir -p "$ROOT/deploy"
cp -f "$GATEWAY_FAT" "$ROOT/deploy/gateway-prod.jar"

rsync -avz --progress \
  "$ROOT/deploy/gateway-prod.jar" \
  "$REMOTE:${REMOTE_DIR}/deploy/gateway-prod.jar"

rsync -avz --progress \
  "$ROOT/gateway/target/"*.jar \
  "$REMOTE:${REMOTE_DIR}/gateway/target/"

rsync -avz --progress \
  "$ROOT/backend/target/"miniandmore-*.jar \
  "$REMOTE:${REMOTE_DIR}/backend/target/"

rsync -avz --progress \
  "$ROOT/store/target/"*.jar \
  "$REMOTE:${REMOTE_DIR}/store/target/"

rsync -avz --progress \
  "$ROOT/commerce/target/"*.jar \
  "$REMOTE:${REMOTE_DIR}/commerce/target/"

rsync -avz --progress \
  "$ROOT/integration/target/"*.jar \
  "$REMOTE:${REMOTE_DIR}/integration/target/"

rsync -avz \
  "$ROOT/deploy/scripts/"*.sh \
  "$REMOTE:${REMOTE_DIR}/deploy/scripts/"

echo ""
echo "=== Starting isolated microservices on VPS (monolith :8081 untouched) ==="
ssh "$REMOTE" "chmod +x ${REMOTE_DIR}/deploy/scripts/*.sh && REPO_ROOT=${REMOTE_DIR} bash ${REMOTE_DIR}/deploy/scripts/prod-microservices-isolated-pm2.sh"

echo ""
echo "Done. Monolith on :8081 is unchanged."
echo "Test from laptop: ssh -L 9180:127.0.0.1:9180 ${REMOTE}"
echo "Then: curl http://localhost:9180/api/v1/categories"
