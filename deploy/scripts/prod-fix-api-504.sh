#!/usr/bin/env bash
# Fix UAT 504 on /api/* — store often still points at catalog :8084 instead of :9184.
# Run on VPS: REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-fix-api-504.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
STORE_PORT="${STORE_PORT:-9181}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi

STORE_JAR=$(ls -1 "${REPO_ROOT}"/services/store/target/miniandmore-store-*.jar 2>/dev/null | head -1)
if [ -z "$STORE_JAR" ]; then
  echo "ERROR: store JAR missing under ${REPO_ROOT}/services/store/target" >&2
  exit 1
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET not set" >&2
  exit 1
fi

echo "=== 1) Catalog health (:${CATALOG_PORT}) ==="
if ! curl -fsS -m 5 "http://127.0.0.1:${CATALOG_PORT}/management/health/liveness" >/dev/null; then
  echo "ERROR: catalog not healthy on :${CATALOG_PORT}. Start catalog first." >&2
  exit 1
fi
echo "OK"

echo "=== 2) Restart store with catalog :${CATALOG_PORT} ==="
pm2 delete mini-prod-ms-store >/dev/null 2>&1 || true
fuser -k "${STORE_PORT}/tcp" >/dev/null 2>&1 || true
sleep 2

export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET

pm2 start java --name mini-prod-ms-store --update-env -- \
  -Xms128m -Xmx384m -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${STORE_PORT}" \
  -Dapplication.catalog-base-url="http://127.0.0.1:${CATALOG_PORT}" \
  -jar "$STORE_JAR" \
  --spring.profiles.active=prod \
  --spring.cloud.consul.enabled=false \
  --spring.cloud.consul.config.enabled=false \
  --spring.liquibase.enabled=false

echo "Waiting for store..."
for i in $(seq 1 40); do
  if curl -fsS -m 3 "http://127.0.0.1:${STORE_PORT}/management/health/liveness" >/dev/null 2>&1; then
    echo "OK  store ready (${i}x5s)"
    break
  fi
  sleep 5
  if [ "$i" -eq 40 ]; then
    echo "ERROR: store not ready" >&2
    pm2 logs mini-prod-ms-store --nostream --lines 30 >&2 || true
    exit 1
  fi
done

echo "=== 3) Test store → catalog ==="
code=$(curl -sS -m 15 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${STORE_PORT}/api/v1/categories" || echo ERR)
echo "store /api/v1/categories → ${code}"
if [ "$code" != "200" ]; then
  echo "WARN: store API not 200 — check pm2 logs mini-prod-ms-store" >&2
fi

echo "=== 4) Restart gateway (optional, ~5–10 min boot) ==="
if [ -x "${REPO_ROOT}/deploy/scripts/prod-restart-isolated-gateway.sh" ]; then
  REPO_ROOT="$REPO_ROOT" bash "${REPO_ROOT}/deploy/scripts/prod-restart-isolated-gateway.sh" || true
else
  echo "Skip gateway restart (script missing). If still 504, restart mini-prod-ms-gateway manually."
fi

echo "=== 5) Verify via gateway ==="
for i in $(seq 1 24); do
  code=$(curl -sS -m 10 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${GATEWAY_PORT}/api/v1/categories" 2>/dev/null || echo ERR)
  if [ "$code" = "200" ]; then
    echo "OK  gateway /api/v1/categories → 200"
    pm2 save
    echo ""
    echo "Public check: curl -fsS https://minisnmore.com/api/v1/categories | head -c 120"
    exit 0
  fi
  echo "... gateway categories ${code} (${i}/24)"
  sleep 10
done

echo "ERROR: gateway still not returning 200" >&2
pm2 logs mini-prod-ms-gateway --nostream --lines 25 >&2 || true
exit 1
