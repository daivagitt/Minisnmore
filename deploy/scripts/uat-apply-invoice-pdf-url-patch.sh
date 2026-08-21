#!/usr/bin/env bash
# Adds customer_order.zoho_invoice_pdf_url (additive, IF NOT EXISTS).
#
# Usage (on UAT VPS):
#   bash deploy/scripts/uat-apply-invoice-pdf-url-patch.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
SQL_FILE="${SCRIPT_DIR}/uat-add-invoice-pdf-url.sql"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

DB_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/miniandmore}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-miniandmore}"
DB_PASS="${SPRING_DATASOURCE_PASSWORD:-}"

DB_HOST=$(echo "$DB_URL" | sed -n 's|jdbc:postgresql://\([^:/]*\).*|\1|p')
DB_PORT=$(echo "$DB_URL" | sed -n 's|jdbc:postgresql://[^:/]*:\([0-9]*\).*|\1|p')
DB_NAME=$(echo "$DB_URL" | sed -n 's|.*/\([^?]*\).*|\1|p')
DB_PORT="${DB_PORT:-5432}"

if [ -z "$DB_PASS" ]; then
  echo "SPRING_DATASOURCE_PASSWORD not set. Source /etc/mini-and-more/uat.env or export it." >&2
  exit 1
fi

echo "=== Applying invoice-pdf-url patch to ${DB_NAME}@${DB_HOST}:${DB_PORT} ==="
export PGPASSWORD="$DB_PASS"
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -v ON_ERROR_STOP=1 -f "$SQL_FILE"

echo ""
echo "=== Verify column ==="
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
  "SELECT column_name, data_type, character_maximum_length FROM information_schema.columns WHERE table_name='customer_order' AND column_name='zoho_invoice_pdf_url';"
