#!/usr/bin/env bash
# Kill hung gateway on :9180 and start fresh (no api-docs profile).
# Run on VPS: REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-hard-restart-gateway.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"
REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
STORE_PORT="${STORE_PORT:-9181}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/gateway-env.sh
source "${SCRIPT_DIR}/lib/gateway-env.sh"

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi

gateway_sanitize_env
gateway_verify_env

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET not set in prod.env" >&2
  exit 1
fi

export LOG_JSON=false
export FRONTEND_AUTH_CALLBACK_URL="${FRONTEND_AUTH_CALLBACK_URL:-https://minisnmore.com/auth/callback}"
export SPRING_R2DBC_URL="r2dbc:postgresql://127.0.0.1:5432/miniandmore"
export SPRING_R2DBC_USERNAME="minisandmore_user"
export SPRING_R2DBC_PASSWORD="${SPRING_R2DBC_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
for _v in GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET APPLE_CLIENT_ID APPLE_CLIENT_SECRET FACEBOOK_CLIENT_ID FACEBOOK_CLIENT_SECRET; do
  [ -n "${!_v:-}" ] || export "${_v}=uat-isolated-placeholder"
done

echo "=== Stop hung gateway (pm2 restart is NOT enough) ==="
pm2 delete mini-prod-ms-gateway >/dev/null 2>&1 || true
pm2 delete mini-uat-ms-gateway >/dev/null 2>&1 || true
sleep 2
# Kill anything still on :9180
fuser -k "${GATEWAY_PORT}/tcp" >/dev/null 2>&1 || true
pkill -f 'gateway-prod.jar' 2>/dev/null || true
pkill -f 'gateway-uat.jar' 2>/dev/null || true
pkill -f 'gateway-0.0.1-SNAPSHOT.jar' 2>/dev/null || true
sleep 2
ss -tlnp | grep ":${GATEWAY_PORT} " && echo "WARN: port still in use" >&2 || echo "OK  port :${GATEWAY_PORT} free"

GATEWAY_JAR="${REPO_ROOT}/deploy/gateway-prod.jar"
if [ ! -f "$GATEWAY_JAR" ] && [ -f "${REPO_ROOT}/deploy/gateway-uat.jar" ]; then
  GATEWAY_JAR="${REPO_ROOT}/deploy/gateway-uat.jar"
fi
if [ ! -f "$GATEWAY_JAR" ]; then
  _src=$(ls -1 "${REPO_ROOT}"/services/gateway/target/gateway-*.jar 2>/dev/null | grep -v '\.original$' | head -1)
  if [ -z "${_src:-}" ]; then
    echo "ERROR: no gateway fat JAR under ${REPO_ROOT}/services/gateway/target or deploy/" >&2
    exit 1
  fi
  mkdir -p "${REPO_ROOT}/deploy"
  install -m 644 "$_src" "${REPO_ROOT}/deploy/gateway-prod.jar"
fi
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
bash "${SCRIPT_DIR}/verify-spring-boot-fat-jar.sh" "$GATEWAY_JAR" "gateway-prod.jar"
mkdir -p "${REPO_ROOT}/services/gateway/target"
ln -sf "../../../deploy/gateway-prod.jar" "${REPO_ROOT}/services/gateway/target/gateway-0.0.1-SNAPSHOT.jar"

echo "=== Start gateway (prod,uat-isolated,api-docs) ==="
unset SPRING_PROFILES_ACTIVE SPRING_PROFILES_INCLUDE

pm2 flush mini-prod-ms-gateway 2>/dev/null || true

export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET
export SPRING_R2DBC_URL SPRING_R2DBC_USERNAME SPRING_R2DBC_PASSWORD

pm2 start java --name mini-prod-ms-gateway --cwd "${REPO_ROOT}" --update-env -- \
  -Xms128m -Xmx384m -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${GATEWAY_PORT}" \
  -jar "$GATEWAY_JAR" \
  --spring.profiles.active=prod,uat-isolated,api-docs \
  --spring.cloud.consul.enabled=false \
  --spring.cloud.consul.config.enabled=false \
  --spring.liquibase.enabled=false \
  --spring.main.allow-circular-references=true \
  --spring.cloud.gateway.server.webflux.httpclient.response-timeout="${GATEWAY_RESPONSE_TIMEOUT}" \
  --jhipster.logging.use-json-format=false \
  --jhipster.logging.logstash.enabled=false
# Secrets and R2DBC via env (SPRING_R2DBC_*, JHIPSTER_*), not argv.

pm2 save
echo "New gateway PID: $(pm2 pid mini-prod-ms-gateway 2>/dev/null || echo unknown)"
echo "Watch ONLY new lines: pm2 logs mini-prod-ms-gateway --lines 0"

echo "=== Wait for Started GatewayApp (up to 15 min) ==="
for i in $(seq 1 90); do
  if curl -fsS -m 5 "http://127.0.0.1:${GATEWAY_PORT}/management/health/liveness" >/dev/null 2>&1; then
    echo "OK  gateway health after ${i}x10s"
    curl -fsS -m 15 "http://127.0.0.1:${GATEWAY_PORT}/api/v1/categories" | head -c 100
    echo ""
    exit 0
  fi
  if pm2 logs mini-prod-ms-gateway --nostream --lines 3 2>/dev/null | grep -q 'Started GatewayApp'; then
    echo "... Started line seen, waiting for port (${i}/90)"
  else
    echo "... booting (${i}/90)"
  fi
  sleep 10
done

echo "ERROR: gateway not healthy — check logs:" >&2
pm2 logs mini-prod-ms-gateway --nostream --lines 40 >&2
exit 1
