#!/usr/bin/env bash
# Restart isolated commerce (:9182) with catalog on :9184 (fixes 503 on /api/v1/order-returns).
# Run on VPS: REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-restart-isolated-commerce.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
COMMERCE_PORT="${COMMERCE_PORT:-9182}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi

export APPLICATION_CATALOG_BASE_URL="http://127.0.0.1:${CATALOG_PORT}"

COMMERCE_JAR=$(ls -1 "${REPO_ROOT}"/services/commerce/target/miniandmore-commerce-*.jar 2>/dev/null | head -1)
if [ -z "$COMMERCE_JAR" ]; then
  echo "ERROR: commerce JAR missing under ${REPO_ROOT}/services/commerce/target" >&2
  exit 1
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET not set" >&2
  exit 1
fi

echo "=== 1) Catalog health (:${CATALOG_PORT}) ==="
if ! curl -fsS -m 5 "http://127.0.0.1:${CATALOG_PORT}/management/health/liveness" >/dev/null; then
  echo "ERROR: catalog not healthy on :${CATALOG_PORT}. Start catalog first:" >&2
  echo "  REPO_ROOT=${REPO_ROOT} bash deploy/scripts/prod-microservices-isolated-pm2.sh" >&2
  exit 1
fi
echo "OK"

echo "=== 2) Restart commerce → catalog :${CATALOG_PORT} ==="
pm2 delete mini-prod-ms-commerce >/dev/null 2>&1 || true
pm2 delete mini-uat-ms-commerce >/dev/null 2>&1 || true
fuser -k "${COMMERCE_PORT}/tcp" >/dev/null 2>&1 || true
sleep 2

pm2 start java --name mini-prod-ms-commerce --update-env -- \
  -Xms128m -Xmx384m -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${COMMERCE_PORT}" \
  -Dapplication.catalog-base-url="http://127.0.0.1:${CATALOG_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -jar "$COMMERCE_JAR" \
  --spring.profiles.active=prod \
  --spring.cloud.consul.enabled=false \
  --spring.cloud.consul.config.enabled=false \
  --spring.liquibase.enabled=false \
  --application.catalog-base-url="http://127.0.0.1:${CATALOG_PORT}"

echo "Waiting for commerce (up to 20 min)..."
for i in $(seq 1 120); do
  if curl -fsS -m 3 "http://127.0.0.1:${COMMERCE_PORT}/management/health/liveness" >/dev/null 2>&1; then
    echo "OK  commerce ready (${i}x10s)"
    break
  fi
  sleep 10
  if [ "$i" -eq 120 ]; then
    echo "ERROR: commerce not ready" >&2
    pm2 logs mini-prod-ms-commerce --nostream --lines 30 >&2 || true
    exit 1
  fi
  if [ $((i % 6)) -eq 0 ]; then
    echo "... commerce still booting (${i}x10s)"
  fi
done

echo "=== 3) Env check ==="
echo "APPLICATION_CATALOG_BASE_URL=${APPLICATION_CATALOG_BASE_URL}"

echo "=== 4) Commerce route (expect 401 without login) ==="
code=$(curl -sS -m 10 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${COMMERCE_PORT}/api/v1/order-returns" || echo ERR)
echo "commerce /api/v1/order-returns → ${code}"
if [ "$code" != "401" ] && [ "$code" != "200" ]; then
  echo "WARN: expected 401 (auth required) or 200 — commerce may still be misconfigured" >&2
fi

echo "=== 5) Via gateway (expect 401 without login, not 503) ==="
gcode=$(curl -sS -m 15 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${GATEWAY_PORT}/api/v1/order-returns" 2>/dev/null || echo ERR)
echo "gateway /api/v1/order-returns → ${gcode}"
if [ "$gcode" = "503" ] || [ "$gcode" = "502" ] || [ "$gcode" = "ERR" ]; then
  echo "ERROR: gateway still cannot reach commerce. Is gateway up on :${GATEWAY_PORT}?" >&2
  exit 1
fi

echo "=== 6) Authenticated admin order-returns (optional) ==="
ADMIN_EMAIL="${PROD_ADMIN_EMAIL:-admin@minisnmore.com}"
if [ -z "${PROD_ADMIN_PASSWORD:-}" ]; then
  echo "WARN: PROD_ADMIN_PASSWORD not set — skipping authed order-returns check" >&2
  ADMIN_PASSWORD=""
else
  ADMIN_PASSWORD="$PROD_ADMIN_PASSWORD"
fi
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
TOKEN=""
if [ -n "$ADMIN_PASSWORD" ]; then
  if ! command -v jq >/dev/null 2>&1; then
    echo "WARN: jq not installed — skipping authed order-returns check" >&2
  else
    AUTH_BODY=$(jq -n --arg username "$ADMIN_EMAIL" --arg password "$ADMIN_PASSWORD" '{username: $username, password: $password}')
    AUTH_RESPONSE=$(curl -fsS -m 15 -X POST "http://127.0.0.1:${GATEWAY_PORT}/api/authenticate" \
      -H "Content-Type: application/json" \
      -d "$AUTH_BODY" 2>/dev/null || echo '{}')
    TOKEN=$(echo "$AUTH_RESPONSE" | jq -r '.id_token // empty')
    if [ -n "$TOKEN" ] && ! echo "$TOKEN" | grep -qE '^[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+$'; then
      echo "WARN: authenticate response id_token does not look like a JWT — skipping authed check" >&2
      TOKEN=""
    fi
  fi
fi
if [ -n "$TOKEN" ]; then
  acode=$(curl -sS -m 30 -o /dev/null -w "%{http_code}" \
    -H "Authorization: Bearer ${TOKEN}" \
    "http://127.0.0.1:${COMMERCE_PORT}/api/v1/admin/order-returns?status=requested" || echo ERR)
  echo "commerce admin/order-returns (authed) → ${acode}"
  if [ "$acode" != "200" ]; then
    echo "WARN: expected 200 with admin token — check APPLICATION_CATALOG_BASE_URL in /etc/mini-and-more/prod.env" >&2
  fi
else
  echo "Skip authed check (no id_token for ${ADMIN_EMAIL})"
fi

pm2 save
echo ""
echo "OK. Refresh Admin → Returns and Account → Returns in the browser."
echo "Diagnose: bash deploy/scripts/prod-diagnose-commerce-catalog.sh"
