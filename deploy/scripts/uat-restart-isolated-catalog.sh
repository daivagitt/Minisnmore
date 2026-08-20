#!/usr/bin/env bash
# Restart isolated catalog core (:9184). Requires Razorpay + internal API key in uat.env (prod profile).
# Usage: REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-restart-isolated-catalog.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

CATALOG_JAR=$(ls -1 "${REPO_ROOT}"/backend/target/miniandmore-*.jar 2>/dev/null | head -1)
if [ -z "$CATALOG_JAR" ]; then
  echo "ERROR: catalog JAR missing under ${REPO_ROOT}/backend/target" >&2
  exit 1
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET not set in uat.env" >&2
  exit 1
fi
if [ -z "${INTERNAL_API_KEY:-}" ]; then
  export INTERNAL_API_KEY="uat-ms-isolated-internal-key"
fi
if [ -z "${RAZORPAY_KEY_ID:-}" ] || [ -z "${RAZORPAY_KEY_SECRET:-}" ]; then
  echo "ERROR: RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set in /etc/mini-and-more/uat.env" >&2
  echo "       Catalog prod profile refuses to start without them (CatalogProductionValidator)." >&2
  echo "       Copy values from the working mini-uat monolith env or Razorpay dashboard." >&2
  exit 1
fi

export INTERNAL_API_KEY
export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET
export ZOHO_INTEGRATION_BASE_URL="http://127.0.0.1:${INTEGRATION_PORT}"
export RAZORPAY_PAYMENT_DEMO="${RAZORPAY_PAYMENT_DEMO:-false}"
export RAZORPAY_REFUND_DEMO="${RAZORPAY_REFUND_DEMO:-false}"

echo "=== Restart catalog core on :${CATALOG_PORT} ==="
pm2 delete mini-uat-ms-catalog-core >/dev/null 2>&1 || true
fuser -k "${CATALOG_PORT}/tcp" >/dev/null 2>&1 || true
sleep 2

pm2 start java --name mini-uat-ms-catalog-core --update-env -- \
  -Xms256m -Xmx512m -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${CATALOG_PORT}" \
  -jar "$CATALOG_JAR" \
  --spring.profiles.active=prod,api-docs \
  --spring.cloud.consul.enabled=false \
  --spring.cloud.consul.config.enabled=false \
  --spring.liquibase.enabled=false \
  --jhipster.logging.use-json-format=false \
  --jhipster.logging.logstash.enabled=false

echo "Waiting for catalog (up to 20 min)..."
for i in $(seq 1 120); do
  if curl -fsS -m 3 "http://127.0.0.1:${CATALOG_PORT}/management/health/liveness" >/dev/null 2>&1; then
    echo "OK  catalog ready (${i}x10s)"
    echo "Zoho order auto-sync: catalog → ${ZOHO_INTEGRATION_BASE_URL} (INTERNAL_API_KEY set)"
    if pm2 describe mini-uat-ms-catalog-core 2>/dev/null | grep 'script args' | grep -qE '9183-[^/]|8083-D'; then
      echo "ERROR: PM2 merged JVM flags (e.g. 9183-Djhipster). Do not pass -D secrets on one line." >&2
      pm2 describe mini-uat-ms-catalog-core 2>/dev/null | grep 'script args' >&2 || true
      exit 1
    fi
    pm2 save
    exit 0
  fi
  sleep 10
  if [ "$i" -eq 120 ]; then
    echo "ERROR: catalog not ready — check logs:" >&2
    pm2 logs mini-uat-ms-catalog-core --nostream --lines 40 >&2 || true
    exit 1
  fi
  if [ $((i % 6)) -eq 0 ]; then
    echo "... catalog still booting (${i}x10s)"
  fi
done
