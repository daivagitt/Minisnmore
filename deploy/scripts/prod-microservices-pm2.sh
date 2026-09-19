#!/usr/bin/env bash
# Production PM2 — full microservices stack (replaces monolith on :8081/:8086).
# Layout: gateway :8080 (public via Nginx) | store :8081 | commerce :8082 | integration :8083 | catalog :8084
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
ENV_FILE="${ENV_FILE:-/etc/mini-and-more/prod.env}"

if [ -f "$ENV_FILE" ]; then
  set -a
  # shellcheck source=/dev/null
  . "$ENV_FILE"
  set +a
fi

resolve_jar() {
  ls -1 "$1" 2>/dev/null | head -1
}

GATEWAY_JAR_PATH=$(resolve_jar "${REPO_ROOT}/gateway/target/gateway-"*.jar)
CATALOG_JAR_PATH=$(resolve_jar "${REPO_ROOT}/backend/target/miniandmore-"*.jar)
STORE_JAR_PATH=$(resolve_jar "${REPO_ROOT}/store/target/miniandmore-store-"*.jar)
COMMERCE_JAR_PATH=$(resolve_jar "${REPO_ROOT}/commerce/target/miniandmore-commerce-"*.jar)
INTEGRATION_JAR_PATH=$(resolve_jar "${REPO_ROOT}/integration/target/miniandmore-integration-"*.jar)

missing=0
for name in GATEWAY CATALOG STORE COMMERCE INTEGRATION; do
  var="${name}_JAR_PATH"
  if [ -z "${!var}" ]; then
    echo "Missing JAR for ${name} under ${REPO_ROOT}" >&2
    missing=1
  fi
done
if [ "$missing" -ne 0 ]; then
  echo "Build all services: gateway/, backend/, store/, commerce/, integration/ (mvn -Pprod -DskipTests package)" >&2
  exit 1
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "ERROR: Set JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET in $ENV_FILE" >&2
  exit 1
fi

if [ -z "${INTERNAL_API_KEY:-}" ]; then
  echo "ERROR: Set INTERNAL_API_KEY in $ENV_FILE (catalog core + integration)" >&2
  exit 1
fi

if ! command -v pm2 >/dev/null 2>&1; then
  echo "pm2 is required." >&2
  exit 1
fi

JAVA_OPTS_CATALOG="${JAVA_OPTS_CATALOG:--Xms768m -Xmx1536m -XX:+UseG1GC}"
JAVA_OPTS_GATEWAY="${JAVA_OPTS_GATEWAY:--Xms512m -Xmx1024m -XX:+UseG1GC}"
JAVA_OPTS_STORE="${JAVA_OPTS_STORE:--Xms256m -Xmx512m -XX:+UseG1GC}"
JAVA_OPTS_COMMERCE="${JAVA_OPTS_COMMERCE:--Xms256m -Xmx512m -XX:+UseG1GC}"
JAVA_OPTS_INTEGRATION="${JAVA_OPTS_INTEGRATION:--Xms512m -Xmx1024m -XX:+UseG1GC}"

SPRING_ARGS_PROD=(--spring.profiles.active=prod)

echo "Stopping legacy monolith / old PM2 apps..."
for port in 8080 8081 8082 8083 8084 8086; do
  fuser -k "${port}/tcp" >/dev/null 2>&1 || true
done

for app in \
  mini-prod-gateway mini-prod-catalog mini-prod-catalog-core \
  mini-prod-store mini-prod-commerce mini-prod-integration \
  mini-backend-prod mini-uat mini-prod-backend mini-prod-catalog; do
  pm2 delete "$app" >/dev/null 2>&1 || true
done

echo "Starting catalog core (8084)..."
pm2 start java --name mini-prod-catalog-core -- \
  $JAVA_OPTS_CATALOG \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port=8084 \
  -jar "$CATALOG_JAR_PATH" \
  "${SPRING_ARGS_PROD[@]}"

echo "Starting integration (8083)..."
pm2 start java --name mini-prod-integration -- \
  $JAVA_OPTS_INTEGRATION \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$INTEGRATION_JAR_PATH" \
  "${SPRING_ARGS_PROD[@]}"

echo "Starting commerce (8082)..."
pm2 start java --name mini-prod-commerce -- \
  $JAVA_OPTS_COMMERCE \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$COMMERCE_JAR_PATH" \
  "${SPRING_ARGS_PROD[@]}"

echo "Starting store (8081)..."
pm2 start java --name mini-prod-store -- \
  $JAVA_OPTS_STORE \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$STORE_JAR_PATH" \
  "${SPRING_ARGS_PROD[@]}"

echo "Starting gateway (8080)..."
pm2 start java --name mini-prod-gateway -- \
  $JAVA_OPTS_GATEWAY \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$GATEWAY_JAR_PATH" \
  "${SPRING_ARGS_PROD[@]}"

pm2 save

HEALTH_SCRIPT="$(dirname "$0")/health-check-microservices.sh"
if [ -x "$HEALTH_SCRIPT" ]; then
  echo "Waiting for services..."
  sleep 25
  bash "$HEALTH_SCRIPT" || true
fi

echo ""
echo "Production microservices (Nginx → :8080 only):"
echo "  Gateway:     http://127.0.0.1:8080"
echo "  Store:       http://127.0.0.1:8081"
echo "  Commerce:    http://127.0.0.1:8082"
echo "  Integration: http://127.0.0.1:8083"
echo "  Catalog:     http://127.0.0.1:8084 (internal)"
echo "Ensure deploy/nginx/prod.minisnmore.com.locations.conf proxies /api/ to :8080 (not :8086)."
