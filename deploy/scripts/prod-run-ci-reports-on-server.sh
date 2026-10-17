#!/usr/bin/env bash
# Full CI + branded HTML reports on the production VPS (tests run on server, not laptop).
#
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-run-ci-reports-on-server.sh
#
# Optional env:
#   RUN_UNIT_TESTS=true|false   RUN_API_TESTS=true|false
#   RUN_JAVADOC=true|false      RUN_OPENAPI=true|false   RUN_SONAR=false
#   PUBLISH_REPORTS=true|false  CONTINUE_ON_TEST_FAIL=true|false
#   API_TEST_BASE_URL=http://127.0.0.1:9180
#   RELEASE_ID=202608101200-uat
#   API_TEST_NO_MAIL=true|false (default true: skip every live call that can email via Care@minisnmore.com)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$REPO_ROOT"

RUN_UNIT_TESTS="${RUN_UNIT_TESTS:-true}"
RUN_API_TESTS="${RUN_API_TESTS:-true}"
RUN_JAVADOC="${RUN_JAVADOC:-true}"
RUN_OPENAPI="${RUN_OPENAPI:-true}"
RUN_SONAR="${RUN_SONAR:-true}"
PUBLISH_REPORTS="${PUBLISH_REPORTS:-true}"
CONTINUE_ON_TEST_FAIL="${CONTINUE_ON_TEST_FAIL:-true}"
API_TEST_BASE_URL="${API_TEST_BASE_URL:-http://127.0.0.1:9180}"
API_CATALOG_BASE_URL="${API_CATALOG_BASE_URL:-http://127.0.0.1:9184}"
API_INTEGRATION_BASE_URL="${API_INTEGRATION_BASE_URL:-https://minisnmore.com}"
OPENAPI_SNAPSHOT_FALLBACK="${OPENAPI_SNAPSHOT_FALLBACK:-false}"
API_TEST_NO_MAIL="${API_TEST_NO_MAIL:-true}"
export API_TEST_NO_MAIL

PKG="${PKG:-artifacts/reports-site}"
REMOTE="${REMOTE_REPORTS_DIR:-/var/www/uat1/mini_reports}"
RELEASE_ID="${RELEASE_ID:-$(date +%Y%m%d%H%M)-uat}"

# Prefer encrypted prod.env.enc
# shellcheck source=lib/encrypted-env.sh
source "${SCRIPT_DIR}/lib/encrypted-env.sh"
if ! mini_load_host_env prod; then
  echo "ERROR: could not load host env (prod.env.enc or prod.env)." >&2
  exit 1
fi

if [ -f /etc/mini-and-more/sonar-ci.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/sonar-ci.env
  set +a
fi

# Unit tests must never pick up the live Care@minisnmore.com SMTP account from the env file.
unset SPRING_MAIL_HOST SPRING_MAIL_PORT SPRING_MAIL_USERNAME SPRING_MAIL_PASSWORD JHIPSTER_MAIL_FROM \
  ZOHO_MAIL_HOST ZOHO_MAIL_PORT ZOHO_MAIL_USERNAME ZOHO_MAIL_PASSWORD ZOHO_MAIL_FROM

run_step() {
  echo ""
  echo ">>> $*"
  if ! "$@"; then
    if [ "$CONTINUE_ON_TEST_FAIL" = "true" ]; then
      echo "WARNING: step failed (continuing): $*" >&2
      return 0
    fi
    return 1
  fi
}

echo "=== Minis & More CI reports ==="
echo "REPO_ROOT=$REPO_ROOT RELEASE_ID=$RELEASE_ID API_TEST_BASE_URL=$API_TEST_BASE_URL"

echo "=== 1. Base reports from current release ==="
rm -rf "$PKG"
mkdir -p "$PKG"
if [ -d "$REMOTE/current" ]; then
  cp -a "$REMOTE/current/." "$PKG/"
fi
# Scenario suites place orders / refund returns (customer emails); Zoho live + integration coverage are retired.
rm -rf "$PKG/backend/scenario-integration" "$PKG/backend/ledgerly-scenario-integration" \
  "$PKG/backend/zoho-live" "$PKG/backend/coverage/integration" "$PKG/backend/javadoc/integration"

if [ "$RUN_UNIT_TESTS" = "true" ]; then
  echo "=== 2. Frontend unit tests (Vitest + coverage) ==="
  run_step npm --prefix frontend run test:coverage -- \
    --coverage.thresholds.lines=0 \
    --coverage.thresholds.statements=0 \
    --coverage.thresholds.branches=0 \
    --coverage.thresholds.functions=0 \
    --coverage.reportOnFailure=true \
    --reporter=default --reporter=junit --outputFile.junit=coverage/junit.xml

  echo "=== 3. Backend unit tests (gateway, catalog, store, commerce) ==="
  for mod in services/gateway backend services/store services/commerce; do
    echo "  module: $mod"
    run_step bash -c "cd '$REPO_ROOT/$mod' && ./mvnw -q test jacoco:report -DskipITs -Djacoco.haltOnFailure=false -Dmaven.test.failure.ignore=true"
  done

  echo "=== 4. Ledgerly-focused catalog unit tests (optional) ==="
  run_step bash -c "cd '$REPO_ROOT/backend' && ./mvnw -q test \
    -Dtest='Ledgerly*Test,ProductDescriptionCodecTest' \
    -Djacoco.skip=true -Dmodernizer.skip=true -DfailIfNoTests=false" || true
fi

echo "=== 5. Package unit test + JaCoCo coverage HTML ==="
bash jenkins/scripts/package-test-reports.sh "$PKG" || true

if [ "$RUN_JAVADOC" = "true" ]; then
  echo "=== 6. JavaDoc (all microservices) ==="
  for mod_spec in services/gateway:gateway backend:catalog services/store:store services/commerce:commerce; do
    IFS=: read -r mod_dir slug <<< "$mod_spec"
    if [ -d "$PKG/backend/javadoc/$slug/index.html" ]; then
      echo "  keeping existing $slug JavaDoc from current release"
      continue
    fi
    run_step bash -c "cd '$REPO_ROOT/$mod_dir' && ./mvnw -q -Djacoco.skip=true javadoc:javadoc -DskipTests \
      -Dmaven.javadoc.failOnError=false -Ddoclint=none"
    apidocs="$REPO_ROOT/$mod_dir/target/reports/apidocs"
    [ -d "$apidocs" ] || apidocs="$REPO_ROOT/$mod_dir/target/site/apidocs"
    if [ -d "$apidocs" ]; then
      rm -rf "$PKG/backend/javadoc/$slug"
      mkdir -p "$PKG/backend/javadoc/$slug"
      cp -a "$apidocs/." "$PKG/backend/javadoc/$slug/"
    fi
  done
fi

if [ "$RUN_OPENAPI" = "true" ]; then
  echo "=== 7. OpenAPI spec from catalog core (public /api/v1 contract) ==="
  mkdir -p "$PKG/backend/swagger"
  OPENAPI_SNAPSHOT="$REPO_ROOT/deploy/postman/openapi.snapshot.json"
  openapi_fetched=false
  for openapi_url in \
    "${API_CATALOG_BASE_URL}/v3/api-docs/catalog" \
    "${API_CATALOG_BASE_URL}/v3/api-docs" \
    "${API_INTEGRATION_BASE_URL}/v3/api-docs/catalog" \
    "${API_INTEGRATION_BASE_URL}/v3/api-docs" \
    "${API_INTEGRATION_BASE_URL}/api/v3/api-docs" \
    "${API_TEST_BASE_URL}/api/v3/api-docs"; do
    if curl -fsSL "$openapi_url" -o "$PKG/backend/swagger/openapi.json"; then
      if python3 -c "import json; json.load(open('$PKG/backend/swagger/openapi.json'))" 2>/dev/null; then
        echo "  fetched OpenAPI from $openapi_url"
        openapi_fetched=true
        cp -f "$PKG/backend/swagger/openapi.json" "$OPENAPI_SNAPSHOT"
        break
      fi
    fi
  done
  if [ "$openapi_fetched" = false ] && [ -f "$OPENAPI_SNAPSHOT" ] && [ "$OPENAPI_SNAPSHOT_FALLBACK" = "true" ]; then
    cp -f "$OPENAPI_SNAPSHOT" "$PKG/backend/swagger/openapi.json"
    echo "  using OpenAPI snapshot from deploy/postman/openapi.snapshot.json (OPENAPI_SNAPSHOT_FALLBACK=true)"
  elif [ "$openapi_fetched" = false ]; then
    echo "ERROR: could not fetch OpenAPI (set OPENAPI_SNAPSHOT_FALLBACK=true to allow stale snapshot)" >&2
    exit 1
  fi
fi

if [ "$RUN_API_TESTS" = "true" ]; then
  echo "=== 8. Skip live Ledgerly API tests ==="
  rm -rf "$PKG/backend/ledgerly-live" artifacts/ledgerly-live

  echo "=== 11. API lifecycle tests (all OpenAPI endpoints) ==="
  API_TEST_BASE_URL="$API_INTEGRATION_BASE_URL" \
    run_step bash jenkins/scripts/run-api-lifecycle-ci.sh
  mkdir -p "$PKG/backend/api"
  cp -a artifacts/api-tests/. "$PKG/backend/api/" 2>/dev/null || true

  echo "=== 11b. End-to-end positive / negative scenarios (all modules) ==="
  run_step bash jenkins/scripts/run-e2e-scenario-tests.sh
  mkdir -p "$PKG/backend/e2e-scenarios"
  cp -a artifacts/e2e-scenarios/. "$PKG/backend/e2e-scenarios/" 2>/dev/null || true
fi

echo "=== 11c. Manual UI tests (positive / negative, codebase) ==="
run_step bash jenkins/scripts/run-manual-ui-tests.sh
mkdir -p "$PKG/manual-ui-tests"
cp -a artifacts/manual-ui-tests/. "$PKG/manual-ui-tests/" 2>/dev/null || true

if [ "$RUN_SONAR" = "true" ]; then
  echo "=== 12. SonarQube analysis + summary ==="
  chmod +x jenkins/scripts/run-sonar-ci.sh
  run_step bash jenkins/scripts/run-sonar-ci.sh
  if [ -d artifacts/sonar-summary ]; then
    mkdir -p "$PKG/sonar-summary"
    cp -a artifacts/sonar-summary/. "$PKG/sonar-summary/"
  fi
fi

echo "=== 13. Ledgerly indices ==="
python3 jenkins/scripts/generate-ledgerly-unit-tests-index.py \
  --reports-dir backend/target/surefire-reports \
  --output "$PKG/backend/ledgerly-unit-tests/index.html" || true
python3 jenkins/scripts/generate-ledgerly-javadoc-index.py \
  --javadocs-dir "$PKG/backend/javadoc" \
  --output "$PKG/backend/javadoc/ledgerly/index.html" || true
python3 jenkins/scripts/generate-javadocs-index.py \
  --javadocs-dir "$PKG/backend/javadoc" \
  --output "$PKG/backend/javadoc/index.html" || true

echo "=== 14. Reports index ==="
python3 jenkins/scripts/generate-reports-site.py --output-dir "$PKG"

if [ "$PUBLISH_REPORTS" = "true" ]; then
  echo "=== 15. Publish to $REMOTE ==="
  RELEASE_PATH="$REMOTE/releases/$RELEASE_ID"
  mkdir -p "$REMOTE/releases"
  rsync -a --delete "$PKG/" "$RELEASE_PATH/"
  ln -sfn "$RELEASE_PATH" "$REMOTE/current"
  echo "Published: https://minisnmore.com/reports/ ($RELEASE_ID)"
else
  echo "Skipping publish (PUBLISH_REPORTS=false). Package at: $PKG"
fi

echo "=== Done ==="
