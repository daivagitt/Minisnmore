#!/usr/bin/env bash
# Restart only the isolated UAT gateway on :9180 (does not touch monolith or other MS).
# Usage: REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-restart-isolated-gateway.sh
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/gateway-env.sh
source "${SCRIPT_DIR}/lib/gateway-env.sh"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

uat_gateway_sanitize_env
uat_gateway_verify_env

export FRONTEND_AUTH_CALLBACK_URL="${FRONTEND_AUTH_CALLBACK_URL:-https://minisnmore.com/auth/callback}"

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ] && command -v pm2 >/dev/null 2>&1; then
  JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET=$(
    pm2 jlist 2>/dev/null | python3 -c "
import json, sys
for p in json.load(sys.stdin):
    if p.get('name') == 'mini-uat':
        print(p.get('pm2_env', {}).get('JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET', ''))
        break
" 2>/dev/null || true
  )
  export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET not set (check uat.env or mini-uat PM2 env)." >&2
  exit 1
fi

export LOG_JSON=false
STORE_PORT="${STORE_PORT:-9181}"
COMMERCE_PORT="${COMMERCE_PORT:-9182}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"

# uat.env often targets a separate gateway DB (user gateway @ :5433). Isolated UAT uses miniandmore jhi_* tables.
if [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  export SPRING_R2DBC_PASSWORD="${SPRING_DATASOURCE_PASSWORD}"
  export SPRING_LIQUIBASE_PASSWORD="${SPRING_DATASOURCE_PASSWORD}"
fi
export SPRING_R2DBC_PASSWORD="${SPRING_R2DBC_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}"
export SPRING_R2DBC_URL="r2dbc:postgresql://127.0.0.1:5432/miniandmore"
export SPRING_R2DBC_USERNAME="minisandmore_user"
export SPRING_LIQUIBASE_URL="jdbc:postgresql://127.0.0.1:5432/miniandmore"
export SPRING_LIQUIBASE_USERNAME="minisandmore_user"

# prod profile requires non-empty OAuth client ids; uat.env often has APPLE_CLIENT_ID= (empty).
# Bash ${VAR:-x} does not replace empty string — export placeholders when blank.
for _oauth_var in GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET APPLE_CLIENT_ID APPLE_CLIENT_SECRET FACEBOOK_CLIENT_ID FACEBOOK_CLIENT_SECRET; do
  if [ -z "${!_oauth_var:-}" ]; then
    echo "WARN: ${_oauth_var} is empty — OAuth for that provider will fail (set in /etc/mini-and-more/uat.env)." >&2
    export "${_oauth_var}=uat-isolated-placeholder"
  fi
done
if [ "${FACEBOOK_CLIENT_ID:-}" = "uat-isolated-placeholder" ]; then
  echo "WARN: Facebook login will return server_error until real FACEBOOK_CLIENT_ID/SECRET are in uat.env." >&2
fi

GATEWAY_JAR_SRC=$(ls -1 "${REPO_ROOT}"/gateway/target/gateway-*.jar 2>/dev/null | head -1 || true)
if [ -z "$GATEWAY_JAR_SRC" ] || [ ! -f "$GATEWAY_JAR_SRC" ]; then
  GATEWAY_JAR_SRC="${REPO_ROOT}/deploy/gateway-uat.jar"
fi
if [ ! -f "$GATEWAY_JAR_SRC" ]; then
  echo "ERROR: gateway JAR not found (build gateway or copy to deploy/gateway-uat.jar)" >&2
  exit 1
fi

# PM2 must not use gateway/target/*.jar — Maven repackage can corrupt it mid-read.
GATEWAY_JAR_PATH="${REPO_ROOT}/deploy/gateway-uat.jar"
mkdir -p "${REPO_ROOT}/deploy"
# gateway/target/*.jar is often a symlink to deploy/gateway-uat.jar — compare inodes, not path strings.
if [ ! "$GATEWAY_JAR_SRC" -ef "$GATEWAY_JAR_PATH" ]; then
  install -m 644 "$GATEWAY_JAR_SRC" "$GATEWAY_JAR_PATH"
fi
if ! unzip -t -q "$GATEWAY_JAR_PATH"; then
  echo "ERROR: gateway JAR failed integrity check: ${GATEWAY_JAR_PATH}" >&2
  exit 1
fi

# spring-boot-properties-migrator (in current JARs) reads gateway/target/*.jar when cwd is gateway/.
mkdir -p "${REPO_ROOT}/gateway/target"
ln -sf "../../deploy/gateway-uat.jar" "${REPO_ROOT}/gateway/target/gateway-0.0.1-SNAPSHOT.jar"

STORE_PORT="${STORE_PORT:-9181}"
COMMERCE_PORT="${COMMERCE_PORT:-9182}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
for _svc in "store:${STORE_PORT}" "commerce:${COMMERCE_PORT}" "catalog:${CATALOG_PORT}"; do
  _name="${_svc%%:*}"
  _port="${_svc##*:}"
  if ! curl -fsS -m 5 "http://127.0.0.1:${_port}/management/health/liveness" >/dev/null 2>&1; then
    echo "WARN: ${_name} :${_port} not healthy — gateway may return 'connection closed' errors until stack is up." >&2
  fi
done
if ! curl -fsS -m 5 "http://127.0.0.1:${STORE_PORT}/management/health/liveness" >/dev/null 2>&1; then
  echo "      Start stack: REPO_ROOT=${REPO_ROOT} bash deploy/scripts/uat-microservices-isolated-pm2.sh" >&2
fi

if [ -z "${SPRING_R2DBC_PASSWORD:-}" ]; then
  echo "ERROR: SPRING_R2DBC_PASSWORD not set (set in /etc/mini-and-more/uat.env or SPRING_DATASOURCE_PASSWORD)." >&2
  exit 1
fi

pm2 delete mini-uat-ms-gateway >/dev/null 2>&1 || true
fuser -k "${GATEWAY_PORT}/tcp" >/dev/null 2>&1 || true
unset SPRING_PROFILES_ACTIVE SPRING_PROFILES_INCLUDE

pm2 start java --name mini-uat-ms-gateway --cwd "${REPO_ROOT}" --update-env -- \
  -Xms128m -Xmx384m -XX:+UseG1GC \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${GATEWAY_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -jar "$GATEWAY_JAR_PATH" \
  --spring.profiles.active=prod,uat-isolated \
  --springdoc.api-docs.enabled=false \
  --springdoc.swagger-ui.enabled=false \
  --spring.cloud.consul.enabled=false \
  --spring.cloud.consul.config.enabled=false \
  --spring.liquibase.enabled=false \
  --spring.main.allow-circular-references=true \
  --spring.cloud.gateway.server.webflux.httpclient.response-timeout="${GATEWAY_RESPONSE_TIMEOUT}" \
  --spring.r2dbc.url="${SPRING_R2DBC_URL}" \
  --spring.r2dbc.username="${SPRING_R2DBC_USERNAME}" \
  --spring.r2dbc.password="${SPRING_R2DBC_PASSWORD}" \
  --jhipster.logging.use-json-format=false \
  --jhipster.logging.logstash.enabled=false \
  --application.register-auto-activate="${REGISTER_AUTO_ACTIVATE:-false}"
# spring.r2dbc.password from SPRING_R2DBC_PASSWORD env (--update-env), not argv

pm2 save
echo "Gateway starting on :${GATEWAY_PORT} (jar: ${GATEWAY_JAR_PATH})"
echo "Routes: store=${MINIANDMORE_STORE_URI} commerce=${MINIANDMORE_COMMERCE_URI} integration=${MINIANDMORE_INTEGRATION_URI}"
echo "Boot on UAT often takes 3–15 minutes with 6 JVMs — do not curl immediately."
echo "Watch: pm2 logs mini-uat-ms-gateway --lines 0"

echo "=== Wait for gateway health (up to 15 min) ==="
for i in $(seq 1 90); do
  if curl -fsS -m 5 "http://127.0.0.1:${GATEWAY_PORT}/management/health/liveness" >/dev/null 2>&1; then
    echo "OK  gateway health after ${i}x10s"
    curl -fsS -m 15 "http://127.0.0.1:${GATEWAY_PORT}/api/v1/categories" | head -c 120
    echo ""
    exit 0
  fi
  if ss -tlnp 2>/dev/null | grep -q ":${GATEWAY_PORT} "; then
    echo "... port :${GATEWAY_PORT} listening, health not ready yet (${i}/90)"
  elif pm2 logs mini-uat-ms-gateway --nostream --lines 5 2>/dev/null | grep -q 'Started GatewayApp'; then
    echo "... Started GatewayApp seen, waiting for health (${i}/90)"
  else
    echo "... booting (${i}/90) — pm2 logs mini-uat-ms-gateway --lines 20"
  fi
  sleep 10
done

echo "ERROR: gateway not healthy after 15 min — last log lines:" >&2
pm2 logs mini-uat-ms-gateway --nostream --lines 50 >&2
exit 1
