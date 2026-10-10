#!/usr/bin/env bash
# Start an isolated microservices stack on 9180–9184 without touching the monolith on :8081.
#
# Database safety (UAT):
#   - Catalog Liquibase migrations run once, before any service is stopped or a new JAR serves traffic.
#   - Runtime Liquibase remains disabled on catalog, integration, and gateway.
#   - Never uses the "faker" Liquibase context (no mock products/users/orders).
#   - Catalog/integration connect read/write to existing miniandmore data only; do not run against prod DB.
#
# Usage (on UAT VPS after JARs are in REPO_ROOT):
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-microservices-isolated-pm2.sh
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/gateway-env.sh
source "${SCRIPT_DIR}/lib/gateway-env.sh"
GATEWAY_JAR="${GATEWAY_JAR:-$REPO_ROOT/gateway/target/gateway-*.jar}"
CATALOG_JAR="${CATALOG_JAR:-$REPO_ROOT/backend/target/miniandmore-*.jar}"
STORE_JAR="${STORE_JAR:-$REPO_ROOT/store/target/miniandmore-store-*.jar}"
COMMERCE_JAR="${COMMERCE_JAR:-$REPO_ROOT/commerce/target/miniandmore-commerce-*.jar}"
INTEGRATION_JAR="${INTEGRATION_JAR:-$REPO_ROOT/integration/target/miniandmore-integration-*.jar}"

GATEWAY_PORT="${GATEWAY_PORT:-9180}"
STORE_PORT="${STORE_PORT:-9181}"
COMMERCE_PORT="${COMMERCE_PORT:-9182}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"
CATALOG_PORT="${CATALOG_PORT:-9184}"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

uat_gateway_sanitize_env

# uat.env may set SERVER_PORT=8081 for the monolith — must not override isolated :918x ports.
unset SERVER_PORT

# uat.env sets monolith-sized JVM heaps (512m–1g); five of those on a busy VPS boot very slowly.
unset JAVA_OPTS_CATALOG JAVA_OPTS_GATEWAY JAVA_OPTS_STORE JAVA_OPTS_COMMERCE JAVA_OPTS_INTEGRATION

export APPLICATION_CATALOG_BASE_URL="http://127.0.0.1:${CATALOG_PORT}"
export APPLICATION_ZOHO_INTEGRATION_BASE_URL="http://127.0.0.1:${INTEGRATION_PORT}"
export ZOHO_INTEGRATION_BASE_URL="http://127.0.0.1:${INTEGRATION_PORT}"
# uat.env may contain INTERNAL_API_KEY= (empty) — treat blank like unset.
if [ -z "${INTERNAL_API_KEY:-}" ]; then
  export INTERNAL_API_KEY="uat-ms-isolated-internal-key"
fi
if [ -z "${RAZORPAY_KEY_ID:-}" ] || [ -z "${RAZORPAY_KEY_SECRET:-}" ]; then
  echo "ERROR: RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET must be set in /etc/mini-and-more/uat.env for catalog (prod profile)." >&2
  exit 1
fi

MONOLITH_JAR="${MONOLITH_JAR:-/var/www/uat1/mini_and_more/current/backend/target/minisandmore-0.0.1-SNAPSHOT.jar}"

# JWT must match monolith + edge services. uat.env often has JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET= (empty).
if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  if command -v pm2 >/dev/null 2>&1; then
    _jwt_from_pm2=$(
      pm2 jlist 2>/dev/null | python3 -c "
import json, sys
for proc in json.load(sys.stdin):
    if proc.get('name') == 'mini-uat':
        print(proc.get('pm2_env', {}).get('JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET', ''))
        break
" 2>/dev/null || true
    )
    if [ -n "$_jwt_from_pm2" ]; then
      export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET="$_jwt_from_pm2"
    fi
  fi
fi
if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  export JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-NDE1ODQ3YWQ4OTYxMDVhNTdjOGYyZTliZjYwNzRkNjc4MGQyYzM4NDI0ZGFhOWNhMmZmNzExZDY2M2Y1NjA1YzNiNGUxOTliMGNiYTQxYWY5NzJkMWMxZTg0MzVjZDc1OWU0ZTM3YjYyMmZjYzhjZjNlOWJiY2VhNjlhYWI3OTA=}"
fi
if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: Set JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET in /etc/mini-and-more/uat.env (must match mini-uat monolith)." >&2
  exit 1
fi
# Gateway JWT puts roles in the "auth" claim (space-separated); edge services must match catalog/gateway.
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITIES_CLAIM_NAME="${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITIES_CLAIM_NAME:-auth}"
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITY_PREFIX="${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITY_PREFIX:-}"
export SPRING_DATASOURCE_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/miniandmore}"
export SPRING_DATASOURCE_USERNAME="${SPRING_DATASOURCE_USERNAME:-minisandmore_user}"
if [ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  if [ -f "$MONOLITH_JAR" ]; then
    SPRING_DATASOURCE_PASSWORD=$(
      unzip -p "$MONOLITH_JAR" BOOT-INF/classes/config/application-prod.yml 2>/dev/null \
        | awk -F': ' '/password:/{print $2; exit}' \
        | sed -n 's/.*\${[^:]*:\([^}]*\)}.*/\1/p'
    )
    export SPRING_DATASOURCE_PASSWORD
  fi
fi
if [ -z "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  echo "WARN: SPRING_DATASOURCE_PASSWORD not set; catalog/integration may fail to connect." >&2
fi
if [ -n "${SPRING_DATASOURCE_PASSWORD:-}" ]; then
  export SPRING_R2DBC_PASSWORD="${SPRING_R2DBC_PASSWORD:-$SPRING_DATASOURCE_PASSWORD}"
  export SPRING_LIQUIBASE_PASSWORD="${SPRING_LIQUIBASE_PASSWORD:-$SPRING_DATASOURCE_PASSWORD}"
fi

# Prod JSON logging needs logstash encoder on classpath; disable to avoid startup crash.
export LOG_JSON=false
export FRONTEND_AUTH_CALLBACK_URL="${FRONTEND_AUTH_CALLBACK_URL:-https://minisnmore.com/auth/callback}"

# Gateway prod profile requires non-empty OAuth client ids (env often has APPLE_CLIENT_ID=).
# Bash ${VAR:-x} does NOT replace empty string — force placeholders when blank.
for _oauth_var in GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET APPLE_CLIENT_ID APPLE_CLIENT_SECRET FACEBOOK_CLIENT_ID FACEBOOK_CLIENT_SECRET; do
  if [ -z "${!_oauth_var:-}" ]; then
    export "${_oauth_var}=prod-isolated-placeholder"
  fi
done

# Integration prod validator requires https Zoho/oauth URLs (not localhost defaults).
export ZOHO_OAUTH_HOMEPAGE_URL="${ZOHO_OAUTH_HOMEPAGE_URL:-https://minisnmore.com}"
export ZOHO_OAUTH_REDIRECT_URI="${ZOHO_OAUTH_REDIRECT_URI:-https://minisnmore.com}"
export ZOHO_OAUTH_ADMIN_REDIRECT_URI="${ZOHO_OAUTH_ADMIN_REDIRECT_URI:-https://minisnmore.com/admin/zoho}"
export ZOHO_OAUTH_JAVASCRIPT_DOMAIN="${ZOHO_OAUTH_JAVASCRIPT_DOMAIN:-https://minisnmore.com}"
export ZOHO_OAUTH_JAVASCRIPT_DOMAIN_ALT="${ZOHO_OAUTH_JAVASCRIPT_DOMAIN_ALT:-https://www.minisnmore.com}"

# Gateway auth: reuse existing jhi_* tables in miniandmore (ignore uat.env gateway@5433 defaults).
export SPRING_R2DBC_URL="r2dbc:postgresql://127.0.0.1:5432/miniandmore"
export SPRING_R2DBC_USERNAME="minisandmore_user"
export SPRING_LIQUIBASE_URL="jdbc:postgresql://127.0.0.1:5432/miniandmore"
export SPRING_LIQUIBASE_USERNAME="minisandmore_user"

if ! command -v pm2 >/dev/null 2>&1; then
  echo "pm2 is required on the UAT host." >&2
  exit 1
fi

resolve_jar() {
  # shellcheck disable=SC2086
  ls -1 $1 2>/dev/null | grep -v '\.original$' | head -1
}

verify_fat_jar() {
  local jar="$1"
  local label="$2"
  local script_dir
  script_dir="$(cd "$(dirname "$0")" && pwd)"
  if [ -x "${script_dir}/verify-spring-boot-fat-jar.sh" ]; then
    bash "${script_dir}/verify-spring-boot-fat-jar.sh" "$jar" "$label"
  fi
}

if [ -f "${REPO_ROOT}/deploy/gateway-uat.jar" ]; then
  GATEWAY_JAR_PATH="${REPO_ROOT}/deploy/gateway-uat.jar"
else
  GATEWAY_JAR_PATH=$(resolve_jar "$GATEWAY_JAR")
fi
verify_fat_jar "$GATEWAY_JAR_PATH" "gateway"
CATALOG_JAR_PATH=$(resolve_jar "$CATALOG_JAR")
STORE_JAR_PATH=$(resolve_jar "$STORE_JAR")
COMMERCE_JAR_PATH=$(resolve_jar "$COMMERCE_JAR")
INTEGRATION_JAR_PATH=$(resolve_jar "$INTEGRATION_JAR")

missing=0
for name in GATEWAY CATALOG STORE COMMERCE INTEGRATION; do
  var="${name}_JAR_PATH"
  if [ -z "${!var}" ]; then
    echo "Missing JAR for ${name} (expected under ${REPO_ROOT})" >&2
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  echo "Build all services locally or via Jenkins, then copy JARs into ${REPO_ROOT}." >&2
  exit 1
fi

echo "JARs:" >&2
echo "  gateway:     ${GATEWAY_JAR_PATH}" >&2
echo "  catalog:     ${CATALOG_JAR_PATH}" >&2
echo "  store:       ${STORE_JAR_PATH}" >&2
echo "  commerce:    ${COMMERCE_JAR_PATH}" >&2
echo "  integration: ${INTEGRATION_JAR_PATH}" >&2

# Fail before stopping the healthy stack if the release schema cannot be applied.
while IFS= read -r changelog || [ -n "${changelog:-}" ]; do
  case "$changelog" in ''|'#'*) continue ;; esac
  CATALOG_LIQUIBASE_CHANGELOG="$changelog" bash "${SCRIPT_DIR}/run-catalog-liquibase.sh" "$CATALOG_JAR_PATH"
done < "${SCRIPT_DIR}/catalog-isolated-migrations.list"

# UAT VPS is often memory/CPU constrained — smaller heaps boot faster than 5 parallel 1GB JVMs.
JAVA_OPTS_CATALOG="-Xms256m -Xmx512m -XX:+UseG1GC"
JAVA_OPTS_GATEWAY="-Xms128m -Xmx384m -XX:+UseG1GC"
JAVA_OPTS_STORE="-Xms128m -Xmx384m -XX:+UseG1GC"
JAVA_OPTS_COMMERCE="-Xms128m -Xmx384m -XX:+UseG1GC"
JAVA_OPTS_INTEGRATION="-Xms256m -Xmx512m -XX:+UseG1GC"

# UAT VPS often needs 15–20+ min per JVM; 600s caused false "not ready" WARNs.
BOOT_WAIT_SECS="${BOOT_WAIT_SECS:-1800}"

wait_for_port() {
  local port="$1"
  local label="$2"
  local pm2_name="$3"
  local elapsed=0
  echo "Waiting for ${label} on :${port} (up to ${BOOT_WAIT_SECS}s)..."
  while [ "$elapsed" -lt "$BOOT_WAIT_SECS" ]; do
    if curl -fsS -m 3 "http://127.0.0.1:${port}/management/health/liveness" >/dev/null 2>&1; then
      echo "OK  ${label} ready on :${port} (${elapsed}s)"
      return 0
    fi
    sleep 15
    elapsed=$((elapsed + 15))
    if [ $((elapsed % 60)) -eq 0 ]; then
      echo "... ${label} still booting (${elapsed}s)"
    fi
  done
  echo "WARN ${label} not ready on :${port} after ${BOOT_WAIT_SECS}s — may still be starting (15–20 min is common on UAT)." >&2
  echo "      Check: pm2 logs ${pm2_name} --lines 40" >&2
  return 1
}

CONSUL_OFF=(--spring.cloud.consul.enabled=false --spring.cloud.consul.config.enabled=false)
# No Liquibase on UAT: schema already owned by monolith; never run faker or seed changelogs.
LIQUIBASE_OFF=(
  --spring.liquibase.enabled=false
  --spring.liquibase.contexts=prod
)
ZOHO_HTTPS_ARGS=(
  --application.zoho.oauth.homepage-url="${ZOHO_OAUTH_HOMEPAGE_URL}"
  --application.zoho.oauth.redirect-uris[0]="${ZOHO_OAUTH_REDIRECT_URI}"
  --application.zoho.oauth.redirect-uris[1]="${ZOHO_OAUTH_ADMIN_REDIRECT_URI}"
  --application.zoho.oauth.javascript-domains[0]="${ZOHO_OAUTH_JAVASCRIPT_DOMAIN}"
  --application.zoho.oauth.javascript-domains[1]="${ZOHO_OAUTH_JAVASCRIPT_DOMAIN_ALT}"
)

# Catalog core serves the public /api/v1 OpenAPI contract (springdoc + path remap).
SPRING_ARGS_CATALOG=(--spring.profiles.active=prod,api-docs --server.port="${CATALOG_PORT}" "${CONSUL_OFF[@]}" "${LIQUIBASE_OFF[@]}")
SPRING_ARGS_GATEWAY=(
  --spring.profiles.active=prod,uat-isolated,api-docs
  --server.port="${GATEWAY_PORT}"
  "${CONSUL_OFF[@]}"
  "${LIQUIBASE_OFF[@]}"
  --spring.main.allow-circular-references=true
  --spring.cloud.gateway.server.webflux.httpclient.response-timeout="${GATEWAY_RESPONSE_TIMEOUT}"
  --spring.r2dbc.url="${SPRING_R2DBC_URL}"
  --spring.r2dbc.username="${SPRING_R2DBC_USERNAME}"
  --jhipster.logging.use-json-format=false
  --jhipster.logging.logstash.enabled=false
)
# spring.r2dbc.password from SPRING_R2DBC_PASSWORD env (--update-env), not argv
SPRING_ARGS_EDGE=(--spring.profiles.active=prod "${CONSUL_OFF[@]}" "${LIQUIBASE_OFF[@]}")
SPRING_ARGS_STORE=(
  --spring.profiles.active=prod
  --server.port="${STORE_PORT}"
  --application.catalog-base-url="http://127.0.0.1:${CATALOG_PORT}"
  "${CONSUL_OFF[@]}"
  "${LIQUIBASE_OFF[@]}"
)
SPRING_ARGS_COMMERCE=(
  --spring.profiles.active=prod
  --server.port="${COMMERCE_PORT}"
  --application.catalog-base-url="http://127.0.0.1:${CATALOG_PORT}"
  "${CONSUL_OFF[@]}"
  "${LIQUIBASE_OFF[@]}"
)
SPRING_ARGS_INTEGRATION=(--spring.profiles.active=prod --server.port="${INTEGRATION_PORT}" "${CONSUL_OFF[@]}" "${LIQUIBASE_OFF[@]}" "${ZOHO_HTTPS_ARGS[@]}")

echo "Liquibase disabled on all isolated services (no mock/faker/seed data)." >&2

# Only recycle isolated ports — never touch :8081 monolith.
for port in "$GATEWAY_PORT" "$STORE_PORT" "$COMMERCE_PORT" "$INTEGRATION_PORT" "$CATALOG_PORT"; do
  fuser -k "${port}/tcp" >/dev/null 2>&1 || true
done

for app in mini-uat-ms-gateway mini-uat-ms-catalog-core mini-uat-ms-store mini-uat-ms-commerce mini-uat-ms-integration; do
  pm2 delete "$app" >/dev/null 2>&1 || true
done

# Start one service at a time — parallel boot on a busy UAT VPS can take 7+ min each and starve CPU.
echo "Starting isolated catalog core (${CATALOG_PORT})..."
pm2 start java --name mini-uat-ms-catalog-core --update-env -- \
  $JAVA_OPTS_CATALOG \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${CATALOG_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -Dapplication.internal-api-key="${INTERNAL_API_KEY}" \
  -Dapplication.zoho-integration.base-url="http://127.0.0.1:${INTEGRATION_PORT}" \
  -jar "$CATALOG_JAR_PATH" \
  "${SPRING_ARGS_CATALOG[@]}"
wait_for_port "$CATALOG_PORT" "catalog core" "mini-uat-ms-catalog-core" || true

echo "Starting isolated integration (${INTEGRATION_PORT})..."
pm2 start java --name mini-uat-ms-integration --update-env -- \
  $JAVA_OPTS_INTEGRATION \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${INTEGRATION_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -Dspring.security.oauth2.resourceserver.jwt.authorities-claim-name=auth \
  -Dspring.security.oauth2.resourceserver.jwt.authority-prefix= \
  -Dapplication.internal-api-key="${INTERNAL_API_KEY}" \
  -jar "$INTEGRATION_JAR_PATH" \
  --spring.security.oauth2.resourceserver.jwt.authorities-claim-name=auth \
  --spring.security.oauth2.resourceserver.jwt.authority-prefix= \
  "${SPRING_ARGS_INTEGRATION[@]}"
wait_for_port "$INTEGRATION_PORT" "integration" "mini-uat-ms-integration" || true

echo "Starting isolated commerce (${COMMERCE_PORT}) → catalog :${CATALOG_PORT}..."
pm2 start java --name mini-uat-ms-commerce --update-env -- \
  $JAVA_OPTS_COMMERCE \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${COMMERCE_PORT}" \
  -Dapplication.catalog-base-url="http://127.0.0.1:${CATALOG_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -jar "$COMMERCE_JAR_PATH" \
  "${SPRING_ARGS_COMMERCE[@]}"
wait_for_port "$COMMERCE_PORT" "commerce" "mini-uat-ms-commerce" || true

echo "Starting isolated store (${STORE_PORT}) → catalog :${CATALOG_PORT}..."
pm2 start java --name mini-uat-ms-store --update-env -- \
  $JAVA_OPTS_STORE \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${STORE_PORT}" \
  -Dapplication.catalog-base-url="http://127.0.0.1:${CATALOG_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -jar "$STORE_JAR_PATH" \
  "${SPRING_ARGS_STORE[@]}"
if ! wait_for_port "$STORE_PORT" "store" "mini-uat-ms-store"; then
  echo "ERROR: store must be healthy before gateway (API routes proxy to :${STORE_PORT})." >&2
  exit 1
fi

echo "Starting isolated gateway (${GATEWAY_PORT})..."
uat_gateway_verify_env
echo "Routes: store=${MINIANDMORE_STORE_URI} commerce=${MINIANDMORE_COMMERCE_URI} integration=${MINIANDMORE_INTEGRATION_URI}"
GATEWAY_JAR_RUN="${REPO_ROOT}/deploy/gateway-uat.jar"
mkdir -p "${REPO_ROOT}/deploy"
if [ ! "$GATEWAY_JAR_PATH" -ef "$GATEWAY_JAR_RUN" ]; then
  install -m 644 "$GATEWAY_JAR_PATH" "$GATEWAY_JAR_RUN"
fi
if ! unzip -t -q "$GATEWAY_JAR_RUN"; then
  echo "ERROR: gateway JAR failed integrity check: ${GATEWAY_JAR_RUN}" >&2
  exit 1
fi
mkdir -p "${REPO_ROOT}/gateway/target"
ln -sf "../../deploy/gateway-uat.jar" "${REPO_ROOT}/gateway/target/gateway-0.0.1-SNAPSHOT.jar"
pm2 start java --name mini-uat-ms-gateway --cwd "${REPO_ROOT}" --update-env -- \
  $JAVA_OPTS_GATEWAY \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port="${GATEWAY_PORT}" \
  -Djhipster.security.authentication.jwt.base64-secret="${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET}" \
  -jar "$GATEWAY_JAR_RUN" \
  "${SPRING_ARGS_GATEWAY[@]}"
wait_for_port "$GATEWAY_PORT" "gateway" "mini-uat-ms-gateway" || true

pm2 save

echo ""
echo "Final boot wait (all 5 JVMs may need several minutes on a busy UAT VPS)..."
for _spec in \
  "${CATALOG_PORT}:catalog core:mini-uat-ms-catalog-core" \
  "${INTEGRATION_PORT}:integration:mini-uat-ms-integration" \
  "${COMMERCE_PORT}:commerce:mini-uat-ms-commerce" \
  "${STORE_PORT}:store:mini-uat-ms-store" \
  "${GATEWAY_PORT}:gateway:mini-uat-ms-gateway"; do
  IFS=: read -r _port _label _pm2 <<< "$_spec"
  wait_for_port "$_port" "$_label" "$_pm2" || true
done

HEALTH_SCRIPT="$(dirname "$0")/health-check-microservices.sh"
if [ -x "$HEALTH_SCRIPT" ]; then
  echo "Running full health check..."
  GATEWAY_URL="http://127.0.0.1:${GATEWAY_PORT}" \
  STORE_URL="http://127.0.0.1:${STORE_PORT}" \
  COMMERCE_URL="http://127.0.0.1:${COMMERCE_PORT}" \
  INTEGRATION_URL="http://127.0.0.1:${INTEGRATION_PORT}" \
  CATALOG_CORE_URL="http://127.0.0.1:${CATALOG_PORT}" \
    bash "$HEALTH_SCRIPT" || true
fi

echo ""
echo "Isolated microservices (monolith on :8081 untouched):"
echo "  Gateway:     http://127.0.0.1:${GATEWAY_PORT}"
echo "  Store:       http://127.0.0.1:${STORE_PORT}"
echo "  Commerce:    http://127.0.0.1:${COMMERCE_PORT}"
echo "  Integration: http://127.0.0.1:${INTEGRATION_PORT}"
echo "  Catalog:     http://127.0.0.1:${CATALOG_PORT}"
echo ""
echo "From your laptop: ssh -L ${GATEWAY_PORT}:127.0.0.1:${GATEWAY_PORT} root@93.127.199.55"
echo "Then: curl http://localhost:${GATEWAY_PORT}/api/v1/categories"
