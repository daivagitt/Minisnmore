#!/usr/bin/env bash
# Apply catalog schema changes from the release JAR without starting HTTP traffic.
set -euo pipefail

CATALOG_JAR_PATH="${1:?Usage: run-catalog-liquibase.sh /path/to/catalog.jar}"
# Prefer the ordered list in catalog-isolated-migrations.list (via prod/uat restart scripts).
# Bare calls must set CATALOG_LIQUIBASE_CHANGELOG — do not default to a late single file
# (that skips post-order / contact / BOGO foundation schema).
if [ -z "${CATALOG_LIQUIBASE_CHANGELOG:-}" ]; then
  echo "ERROR: CATALOG_LIQUIBASE_CHANGELOG is required." >&2
  echo "       Use deploy/scripts/prod-restart-isolated-catalog.sh (or uat-*) so the full" >&2
  echo "       catalog-isolated-migrations.list is applied in order, or export one changelog path." >&2
  exit 1
fi
export CATALOG_LIQUIBASE_CHANGELOG

if [ ! -f "$CATALOG_JAR_PATH" ]; then
  echo "ERROR: catalog JAR not found: ${CATALOG_JAR_PATH}" >&2
  exit 1
fi
if [ -z "${SPRING_LIQUIBASE_URL:-${SPRING_DATASOURCE_URL:-}}" ]; then
  echo "ERROR: SPRING_LIQUIBASE_URL or SPRING_DATASOURCE_URL must be set" >&2
  exit 1
fi
if [ -z "${SPRING_LIQUIBASE_USERNAME:-${SPRING_DATASOURCE_USERNAME:-}}" ]; then
  echo "ERROR: SPRING_LIQUIBASE_USERNAME or SPRING_DATASOURCE_USERNAME must be set" >&2
  exit 1
fi
if [ -z "${SPRING_LIQUIBASE_PASSWORD:-${SPRING_DATASOURCE_PASSWORD:-}}" ]; then
  echo "ERROR: SPRING_LIQUIBASE_PASSWORD or SPRING_DATASOURCE_PASSWORD must be set" >&2
  exit 1
fi
if ! catalog_jar_entries="$(jar tf "$CATALOG_JAR_PATH")"; then
  echo "ERROR: catalog JAR could not be read: ${CATALOG_JAR_PATH}" >&2
  exit 1
fi
if [[ "$catalog_jar_entries" != *"BOOT-INF/classes/com/miniandmore/app/config/LiquibaseMigrationRunner.class"* ]]; then
  echo "ERROR: catalog JAR does not contain the migration runner; rebuild the backend JAR" >&2
  exit 1
fi

echo "Applying production Liquibase migration ${CATALOG_LIQUIBASE_CHANGELOG} before catalog restart..."
java \
  -Dloader.main=com.miniandmore.app.config.LiquibaseMigrationRunner \
  -cp "$CATALOG_JAR_PATH" \
  org.springframework.boot.loader.launch.PropertiesLauncher
