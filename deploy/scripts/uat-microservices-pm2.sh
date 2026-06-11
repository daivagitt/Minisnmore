#!/usr/bin/env bash
# Start or restart UAT1 microservices on the VPS (after Jenkins rsyncs JARs into the release).
# Layout: gateway :8080 (public) | store :8081 | commerce :8082 | integration :8083 | catalog core :8084 (internal)
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_and_more/current}"
GATEWAY_JAR="${GATEWAY_JAR:-$REPO_ROOT/gateway/target/gateway-*.jar}"
CATALOG_JAR="${CATALOG_JAR:-$REPO_ROOT/backend/target/miniandmore-*.jar}"
STORE_JAR="${STORE_JAR:-$REPO_ROOT/store/target/miniandmore-store-*.jar}"
COMMERCE_JAR="${COMMERCE_JAR:-$REPO_ROOT/commerce/target/miniandmore-commerce-*.jar}"
INTEGRATION_JAR="${INTEGRATION_JAR:-$REPO_ROOT/integration/target/miniandmore-integration-*.jar}"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

if ! command -v pm2 >/dev/null 2>&1; then
  echo "pm2 is required on the UAT host." >&2
  exit 1
fi

resolve_jar() {
  ls -1 "$1" 2>/dev/null | head -1
}

GATEWAY_JAR_PATH=$(resolve_jar "$GATEWAY_JAR")
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
  echo "Build all services in Jenkins (BUILD_BACKEND, BUILD_GATEWAY, BUILD_STORE, BUILD_COMMERCE, BUILD_INTEGRATION) or run mvn package locally." >&2
  exit 1
fi

if [ -z "${JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET:-}" ]; then
  echo "WARN: JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET not set; JWT must match on gateway, edges, and core." >&2
fi

export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITIES_CLAIM_NAME="${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITIES_CLAIM_NAME:-auth}"
export SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITY_PREFIX="${SPRING_SECURITY_OAUTH2_RESOURCESERVER_JWT_AUTHORITY_PREFIX:-}"

JAVA_OPTS_CATALOG="${JAVA_OPTS_CATALOG:--Xms512m -Xmx1024m -XX:+UseG1GC}"
JAVA_OPTS_GATEWAY="${JAVA_OPTS_GATEWAY:--Xms256m -Xmx768m -XX:+UseG1GC}"
JAVA_OPTS_STORE="${JAVA_OPTS_STORE:--Xms256m -Xmx512m -XX:+UseG1GC}"
JAVA_OPTS_COMMERCE="${JAVA_OPTS_COMMERCE:--Xms256m -Xmx512m -XX:+UseG1GC}"
JAVA_OPTS_INTEGRATION="${JAVA_OPTS_INTEGRATION:--Xms512m -Xmx1024m -XX:+UseG1GC}"

SPRING_ARGS_CATALOG=(--spring.profiles.active=prod,api-docs)
SPRING_ARGS_GATEWAY=(--spring.profiles.active=prod,api-docs,uat)
SPRING_ARGS_EDGE=(--spring.profiles.active=prod)

for port in 8080 8081 8082 8083 8084; do
  fuser -k "${port}/tcp" >/dev/null 2>&1 || true
done

for app in mini-uat-gateway mini-uat-catalog-core mini-uat-store mini-uat-commerce mini-uat-integration mini-uat-catalog mini-uat mini-uat-backend; do
  pm2 delete "$app" >/dev/null 2>&1 || true
done

echo "Starting catalog core (8084)..."
pm2 start java --name mini-uat-catalog-core -- \
  $JAVA_OPTS_CATALOG \
  -Djava.security.egd=file:/dev/./urandom \
  -Dserver.port=8084 \
  -jar "$CATALOG_JAR_PATH" \
  "${SPRING_ARGS_CATALOG[@]}"

echo "Starting integration (8083)..."
pm2 start java --name mini-uat-integration -- \
  $JAVA_OPTS_INTEGRATION \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$INTEGRATION_JAR_PATH" \
  "${SPRING_ARGS_EDGE[@]}"

echo "Starting commerce (8082)..."
pm2 start java --name mini-uat-commerce -- \
  $JAVA_OPTS_COMMERCE \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$COMMERCE_JAR_PATH" \
  "${SPRING_ARGS_EDGE[@]}"

echo "Starting store (8081)..."
pm2 start java --name mini-uat-store -- \
  $JAVA_OPTS_STORE \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$STORE_JAR_PATH" \
  "${SPRING_ARGS_EDGE[@]}"

echo "Starting gateway (8080)..."
pm2 start java --name mini-uat-gateway -- \
  $JAVA_OPTS_GATEWAY \
  -Djava.security.egd=file:/dev/./urandom \
  -jar "$GATEWAY_JAR_PATH" \
  "${SPRING_ARGS_GATEWAY[@]}"

pm2 save

HEALTH_SCRIPT="$(dirname "$0")/health-check-microservices.sh"
if [ -x "$HEALTH_SCRIPT" ]; then
  echo "Waiting for services..."
  sleep 20
  bash "$HEALTH_SCRIPT" || true
fi

echo "UAT1 microservices:"
echo "  Gateway (public):  http://127.0.0.1:8080"
echo "  Store:           http://127.0.0.1:8081"
echo "  Commerce:        http://127.0.0.1:8082"
echo "  Integration:     http://127.0.0.1:8083"
echo "  Catalog core:    http://127.0.0.1:8084 (internal)"
echo "Nginx: proxy /api/, /oauth2/, /login/oauth2/ → :8080 — deploy/nginx/uat.minisnmore.com.locations.conf"
