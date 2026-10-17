#!/usr/bin/env bash
# Apply additive schema patches on UAT so microservices match the shared DB.
# Does NOT drop user_id — monolith on :8081 keeps working.
#
# Usage (on production VPS):
#   bash deploy/scripts/prod-apply-monolith-safe-schema-patch.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/prod-monolith-safe-schema-patch.sql"

# Prefer encrypted prod.env.enc
# shellcheck source=lib/encrypted-env.sh
source "${SCRIPT_DIR}/lib/encrypted-env.sh"
if ! mini_load_host_env prod; then
  echo "ERROR: could not load host env (prod.env.enc or prod.env)." >&2
  exit 1
fi

DB_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/miniandmore}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-miniandmore}"
DB_PASS="${SPRING_DATASOURCE_PASSWORD:-}"

# Parse jdbc:postgresql://host:port/dbname
DB_HOST=$(echo "$DB_URL" | sed -n 's|jdbc:postgresql://\([^:/]*\).*|\1|p')
DB_PORT=$(echo "$DB_URL" | sed -n 's|jdbc:postgresql://[^:/]*:\([0-9]*\).*|\1|p')
DB_NAME=$(echo "$DB_URL" | sed -n 's|.*/\([^?]*\).*|\1|p')
DB_PORT="${DB_PORT:-5432}"

if [ -z "$DB_PASS" ]; then
  echo "SPRING_DATASOURCE_PASSWORD not set. Source /etc/mini-and-more/prod.env or export it." >&2
  exit 1
fi

echo "=== Applying monolith-safe schema patch to ${DB_NAME}@${DB_HOST}:${DB_PORT} ==="
export PGPASSWORD="$DB_PASS"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE"

echo ""
echo "=== Verify columns ==="
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name = 'site_setting' AND column_name LIKE 'subscribe%' ORDER BY 1;"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name IN ('customer_order','customer_profile') AND column_name IN ('user_id','user_login') ORDER BY table_name, column_name;"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT column_name FROM information_schema.columns WHERE table_name = 'customer_order' AND column_name IN ('inventory_reserved','inventory_reserved_until','user_login','user_id') ORDER BY 1;"

echo ""
echo "=== Re-test catalog core (9184) ==="
curl -s -o /dev/null -w "catalog site-settings: %{http_code}\n" http://127.0.0.1:9184/internal/v1/site-settings
curl -s -o /dev/null -w "micro gateway site-settings: %{http_code}\n" http://127.0.0.1:9180/api/v1/site-settings
curl -s -o /dev/null -w "monolith site-settings: %{http_code}\n" http://127.0.0.1:8081/api/v1/site-settings

echo ""
echo "If catalog still 500, restart isolated stack only:"
echo "  REPO_ROOT=/var/www/uat1/mini_ms_test bash /var/www/uat1/mini_ms_test/deploy/scripts/prod-microservices-isolated-pm2.sh"
