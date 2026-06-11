#!/usr/bin/env bash
# Restart only the isolated UAT integration service on :9183 (does not touch other MS).
# Usage: REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-restart-isolated-integration.sh
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

if [ -z "${INTERNAL_API_KEY:-}" ]; then
  export INTERNAL_API_KEY="uat-ms-isolated-internal-key"
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ] && command -v pm2 >/dev/null 2>&1; then
  JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET=$(
    pm2 jlist 2>/dev/null | python3 -c "
import json, re, sys
for p in json.load(sys.stdin):
    if p.get('name') not in ('mini-uat-ms-gateway', 'mini-uat', 'mini-uat-ms-commerce', 'mini-uat-ms-store'):
        continue
    env = p.get('pm2_env', {})
    v = env.get('JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET', '')
    if v:
        print(v)
        break
    args = env.get('args', []) or []
    for arg in args:
        m = re.search(r'jhipster\\.security\\.authentication\\.jwt\\.base64-secret=([^\\s]+)', str(arg))
        if m:
            print(m.group(1))
            sys.exit(0)
" 2>/dev/null || true
  )
  export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET not set (check uat.env or gateway PM2 env)." >&2
  exit 1
fi

export LOG_JSON=false
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITIES_CLAIM_NAME="${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITIES_CLAIM_NAME:-auth}"
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITY_PREFIX="${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITY_PREFIX:-}"

# S3/R2 (Zoho image import on pull + product image push)
export S3_ENDPOINT="${S3_ENDPOINT:-}"
export S3_REGION="${S3_REGION:-auto}"
export S3_BUCKET="${S3_BUCKET:-}"
export S3_ACCESS_KEY="${S3_ACCESS_KEY:-}"
export S3_SECRET_KEY="${S3_SECRET_KEY:-}"
export S3_PUBLIC_BASE_URL="${S3_PUBLIC_BASE_URL:-}"
export S3_KEY_PREFIX="${S3_KEY_PREFIX:-}"
export S3_MAX_UPLOAD_BYTES="${S3_MAX_UPLOAD_BYTES:-8192000}"

export ZOHO_ORDER_TRACKING_ENABLED="${ZOHO_ORDER_TRACKING_ENABLED:-false}"
export ZOHO_ORDER_TRACKING_FIXED_DELAY_MS="${ZOHO_ORDER_TRACKING_FIXED_DELAY_MS:-3600000}"
export ZOHO_ORDER_RETRY_FIXED_DELAY_MS="${ZOHO_ORDER_RETRY_FIXED_DELAY_MS:-3600000}"

INTEGRATION_JAR_PATH=$(ls -1 "${REPO_ROOT}"/integration/target/miniandmore-integration-*.jar 2>/dev/null | head -1)
if [ -z "$INTEGRATION_JAR_PATH" ] || [ ! -f "$INTEGRATION_JAR_PATH" ]; then
  echo "ERROR: integration JAR not found under ${REPO_ROOT}/integration/target/miniandmore-integration-*.jar" >&2
  exit 1
fi

if ! unzip -t "$INTEGRATION_JAR_PATH" >/dev/null 2>&1; then
  echo "ERROR: integration JAR is corrupt: $INTEGRATION_JAR_PATH" >&2
  exit 1
fi

pm2 delete mini-uat-ms-integration >/dev/null 2>&1 || true
fuser -k "${INTEGRATION_PORT}/tcp" >/dev/null 2>&1 || true
if command -v ss >/dev/null 2>&1; then
  for pid in $(ss -tlnp "sport = :${INTEGRATION_PORT}" 2>/dev/null | grep -oE 'pid=[0-9]+' | cut -d= -f2 | sort -u); do
    kill -9 "$pid" 2>/dev/null || true
  done
fi
sleep 3
if ss -tlnp "sport = :${INTEGRATION_PORT}" 2>/dev/null | grep -q LISTEN; then
  echo "ERROR: port ${INTEGRATION_PORT} still in use after stop — stale JVM?" >&2
  ss -tlnp "sport = :${INTEGRATION_PORT}" >&2 || true
  exit 1
fi

pm2 start java --name mini-uat-ms-integration --update-env -- \
  -Xms256m -Xmx512m -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${INTEGRATION_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -Dspring.security.oauth2.resourceserver.jwt.authorities-claim-name=auth \
  -Dspring.security.oauth2.resourceserver.jwt.authority-prefix= \
  -Dapplication.internal-api-key="${INTERNAL_API_KEY}" \
  -Dapplication.zoho.order-tracking.enabled="${ZOHO_ORDER_TRACKING_ENABLED}" \
  -Dapplication.zoho.order-tracking.fixed-delay-ms="${ZOHO_ORDER_TRACKING_FIXED_DELAY_MS}" \
  -Dapplication.zoho.order-retry.fixed-delay-ms="${ZOHO_ORDER_RETRY_FIXED_DELAY_MS}" \
  -jar "$INTEGRATION_JAR_PATH" \
  --spring.profiles.active=prod \
  --spring.security.oauth2.resourceserver.jwt.authorities-claim-name=auth \
  --spring.security.oauth2.resourceserver.jwt.authority-prefix= \
  --spring.cloud.consul.enabled=false \
  --spring.cloud.consul.config.enabled=false \
  --spring.liquibase.enabled=false \
  --application.zoho.oauth.homepage-url=https://uat.minisnmore.com \
  --application.zoho.oauth.redirect-uris[0]=https://uat.minisnmore.com \
  --application.zoho.oauth.redirect-uris[1]=https://uat.minisnmore.com/admin/zoho \
  --application.zoho.oauth.javascript-domains[0]=https://uat.minisnmore.com \
  --application.zoho.oauth.javascript-domains[1]=https://uat.minisnmore.com

pm2 save
echo "Integration starting on :${INTEGRATION_PORT} (jar: ${INTEGRATION_JAR_PATH})"
echo "Waiting for integration (up to 20 min)..."
for i in $(seq 1 120); do
  if curl -fsS -m 3 "http://127.0.0.1:${INTEGRATION_PORT}/management/health/liveness" >/dev/null 2>&1; then
    echo "OK  integration ready (${i}x10s)"
    break
  fi
  if ! pm2 describe mini-uat-ms-integration 2>/dev/null | grep -qE 'status.*online'; then
    echo "ERROR: mini-uat-ms-integration is not online in pm2" >&2
    pm2 logs mini-uat-ms-integration --nostream --lines 40 >&2 || true
    exit 1
  fi
  sleep 10
  if [ "$i" -eq 120 ]; then
    echo "ERROR: integration not ready on :${INTEGRATION_PORT}" >&2
    pm2 logs mini-uat-ms-integration --nostream --lines 40 >&2 || true
    exit 1
  fi
  if [ $((i % 6)) -eq 0 ]; then
    echo "... integration still booting (${i}x10s)"
  fi
done

PM2_PID=$(pm2 jlist 2>/dev/null | python3 -c "
import json, sys
for p in json.load(sys.stdin):
    if p.get('name') == 'mini-uat-ms-integration':
        print(p.get('pid', ''))
        break
" 2>/dev/null || true)
LISTEN_PID=$(ss -tlnp "sport = :${INTEGRATION_PORT}" 2>/dev/null | grep -oE 'pid=[0-9]+' | head -1 | cut -d= -f2 || true)
echo ""
echo "PM2 pid=${PM2_PID:-?}  listener pid=${LISTEN_PID:-?}  (must match)"
if [ -n "${PM2_PID}" ] && [ -n "${LISTEN_PID}" ] && [ "$PM2_PID" != "$LISTEN_PID" ]; then
  echo "WARN: stale JVM may still own :${INTEGRATION_PORT} — POST /retry-orders can return 403" >&2
fi
echo ""
echo "Smoke: POST /api/v1/admin/zoho/retry-orders (expect 200/502, not 403)"
echo "Watch: pm2 logs mini-uat-ms-integration --nostream --lines 30"
