#!/usr/bin/env bash
# Pre-deploy checks before replacing prod monolith with microservices.
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
ENV_FILE="${ENV_FILE:-/etc/mini-and-more/prod.env}"
NGINX_SNIPPET="${NGINX_SNIPPET:-/etc/nginx/snippets/minisnmore-prod-locations.conf}"
FAIL=0

warn() { echo "WARN  $*" >&2; }
err() { echo "FAIL  $*" >&2; FAIL=1; }
ok() { echo "OK    $*"; }

check_var() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    err "env $name is empty (set in $ENV_FILE)"
  else
    ok "env $name is set"
  fi
}

echo "=== Production microservices preflight ==="
echo "REPO_ROOT=$REPO_ROOT"
echo "ENV_FILE=$ENV_FILE"
echo ""

if [ ! -f "$ENV_FILE" ]; then
  err "Missing $ENV_FILE — copy from deploy/env/prod.env.example"
else
  ok "env file exists"
  set -a
  # shellcheck source=/dev/null
  . "$ENV_FILE"
  set +a
fi

for v in \
  JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET \
  INTERNAL_API_KEY \
  SPRING_DATASOURCE_PASSWORD \
  SPRING_R2DBC_PASSWORD \
  SPRING_LIQUIBASE_PASSWORD \
  GOOGLE_CLIENT_ID \
  GOOGLE_CLIENT_SECRET \
  RAZORPAY_KEY_ID \
  RAZORPAY_KEY_SECRET; do
  check_var "$v"
done

if [ -z "${APPLICATION_CATALOG_BASE_URL:-}" ]; then
  warn "APPLICATION_CATALOG_BASE_URL unset — edges default to http://127.0.0.1:8084"
else
  ok "APPLICATION_CATALOG_BASE_URL=${APPLICATION_CATALOG_BASE_URL}"
fi

echo ""
echo "=== JARs ==="
for spec in \
  "gateway:gateway/target/gateway-*.jar" \
  "catalog:backend/target/miniandmore-*.jar" \
  "store:store/target/miniandmore-store-*.jar" \
  "commerce:commerce/target/miniandmore-commerce-*.jar" \
  "integration:integration/target/miniandmore-integration-*.jar"; do
  label="${spec%%:*}"
  path="${REPO_ROOT}/${spec#*:}"
  if ls -1 $path >/dev/null 2>&1; then
    ok "JAR $label ($(ls -1 $path | head -1))"
  else
    err "Missing JAR $label ($path)"
  fi
done

echo ""
echo "=== Local services (optional) ==="
for svc in "Consul:8500" "Redis:6379" "Postgres:5432"; do
  name="${svc%%:*}"
  port="${svc#*:}"
  if command -v nc >/dev/null 2>&1 && nc -z 127.0.0.1 "$port" 2>/dev/null; then
    ok "$name listening on :$port"
  else
    warn "$name not reachable on :$port (start before PM2 if required)"
  fi
done

echo ""
echo "=== Nginx (public API must hit gateway :8080) ==="
if [ -f "$NGINX_SNIPPET" ]; then
  if grep -q '127.0.0.1:8080/api/' "$NGINX_SNIPPET" 2>/dev/null; then
    ok "Nginx snippet proxies /api/ to :8080"
  else
    err "Nginx snippet does not proxy /api/ to :8080 — update from deploy/nginx/prod.minisnmore.com.locations.conf"
  fi
  if grep -q '127.0.0.1:8086' "$NGINX_SNIPPET" 2>/dev/null; then
    err "Nginx still references legacy monolith :8086 — replace with :8080"
  fi
else
  warn "Nginx snippet not found at $NGINX_SNIPPET — verify manually"
fi

echo ""
echo "=== Legacy PM2 / ports ==="
if command -v pm2 >/dev/null 2>&1; then
  if pm2 jlist 2>/dev/null | grep -qE 'mini-backend-prod|mini-prod-backend'; then
    warn "Legacy monolith PM2 process still listed — prod-microservices-pm2.sh will remove it"
  else
    ok "No legacy mini-backend-prod in pm2 list"
  fi
else
  warn "pm2 not installed"
fi

if [ "$FAIL" -ne 0 ]; then
  echo ""
  echo "Preflight FAILED — fix items above before deploy."
  exit 1
fi

echo ""
echo "Preflight PASSED — safe to build, rsync, and run:"
echo "  bash deploy/scripts/prod-microservices-pm2.sh"
