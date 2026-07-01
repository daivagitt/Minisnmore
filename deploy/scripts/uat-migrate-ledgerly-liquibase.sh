#!/usr/bin/env bash
# Apply Ledgerly migration changesets to an existing miniandmore database.
#
# This script runs ONLY the two Ledgerly changesets (rename zoho → ledgerly and
# webhook_secret / ledgerly_webhook_event) against the already-populated UAT database.
# It does NOT run the full master changelog — that would require authority.csv and other
# seed fixtures that do not exist in a running production/UAT database.
#
# Prerequisites:
#   - Java + REPO_ROOT with a packaged backend JAR
#   - /etc/mini-and-more/uat.env contains DB credentials (or supply env inline)
#
# Usage:
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/uat-migrate-ledgerly-liquibase.sh
#
# Safe to re-run: Liquibase tracks applied changesets in databasechangelog and skips already-run ones.
# If UAT was migrated manually (SQL), insert the changeset rows first — see "Manual DB was applied" below.
#
# Manual DB was applied on UAT (2026-06-30):
#   If these changesets were already applied via raw SQL, insert the tracking rows to avoid re-run:
#
#     INSERT INTO databasechangelog (id, author, filename, dateexecuted, orderexecuted, exectype, md5sum, description, comments, tag, liquibase, contexts, labels, deployment_id)
#     VALUES
#       ('20260701130000-1','miniandmore','config/liquibase/changelog/20260701130000_rename_zoho_to_ledgerly.xml',now(),999,'EXECUTED',null,'createTable tableName=ledgerly_setting','','',null,null,null,'manual_uat_cutover'),
#       ('20260701130000-2','miniandmore','config/liquibase/changelog/20260701130000_rename_zoho_to_ledgerly.xml',now(),1000,'EXECUTED',null,'renameColumn x13','','',null,null,null,'manual_uat_cutover'),
#       ('20260701130000-3','miniandmore','config/liquibase/changelog/20260701130000_rename_zoho_to_ledgerly.xml',now(),1001,'EXECUTED',null,'dropTable tableName=zoho_setting','','',null,null,null,'manual_uat_cutover'),
#       ('20260701140000-1','ledgerly','config/liquibase/changelog/20260701140000_ledgerly_webhook_secret.xml',now(),1002,'EXECUTED',null,'addColumn tableName=ledgerly_setting','','',null,null,null,'manual_uat_cutover'),
#       ('20260701140000-2','ledgerly','config/liquibase/changelog/20260701140000_ledgerly_webhook_secret.xml',now(),1003,'EXECUTED',null,'createTable tableName=ledgerly_webhook_event','','',null,null,null,'manual_uat_cutover'),
#       ('20260630200000-1','miniandmore','config/liquibase/changelog/20260630200000_add_tax_pct_hsn_to_product.xml',now(),1004,'EXECUTED',null,'addColumn tableName=product (hsn, tax_pct)','','',null,null,null,'manual_uat_cutover');
#
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"

if [ -f /etc/mini-and-more/uat.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/uat.env
  set +a
fi

DB_URL="${SPRING_DATASOURCE_URL:-jdbc:postgresql://127.0.0.1:5432/miniandmore}"
DB_USER="${SPRING_DATASOURCE_USERNAME:-minisandmore_user}"
DB_PASS="${SPRING_DATASOURCE_PASSWORD:-}"

if [ -z "$DB_PASS" ]; then
  echo "ERROR: SPRING_DATASOURCE_PASSWORD not set. Source /etc/mini-and-more/uat.env first." >&2
  exit 1
fi

CATALOG_JAR=$(ls -1 "${REPO_ROOT}"/backend/target/miniandmore-*.jar 2>/dev/null | grep -v '\.original$' | head -1)
if [ -z "$CATALOG_JAR" ]; then
  echo "ERROR: No backend JAR found under ${REPO_ROOT}/backend/target/" >&2
  echo "       Build first: cd backend && ./mvnw -Pprod -DskipTests -Djacoco.skip=true -Dmodernizer.skip=true package" >&2
  exit 1
fi

CHANGELOGS=(
  "config/liquibase/changelog/20260701130000_rename_zoho_to_ledgerly.xml"
  "config/liquibase/changelog/20260701140000_ledgerly_webhook_secret.xml"
  "config/liquibase/changelog/20260630200000_add_tax_pct_hsn_to_product.xml"
  "config/liquibase/changelog/20260701150000_fix_tax_pct_default_to_5.xml"
)

echo "=== Ledgerly Liquibase migration ==="
echo "JAR:      $CATALOG_JAR"
echo "DB:       $DB_URL"
echo ""

for changelog in "${CHANGELOGS[@]}"; do
  echo "--- Applying: $changelog ---"
  java -jar "$CATALOG_JAR" \
    --spring.liquibase.change-log="$changelog" \
    --spring.liquibase.enabled=true \
    --spring.datasource.url="$DB_URL" \
    --spring.datasource.username="$DB_USER" \
    --spring.datasource.password="$DB_PASS" \
    --spring.main.web-application-type=none \
    --spring.profiles.active=prod \
    --spring.cloud.consul.enabled=false \
    --spring.cloud.consul.config.enabled=false \
    liquibase:update 2>&1 | grep -E 'Successfully applied|ChangeSet|ERROR|WARN|already been run' || true
  echo ""
done

echo "=== Migration complete. Verify with: ==="
echo "  psql -U ${DB_USER} -d miniandmore -c \"SELECT id, author, dateexecuted FROM databasechangelog WHERE id LIKE '20260701%' ORDER BY orderexecuted;\""
