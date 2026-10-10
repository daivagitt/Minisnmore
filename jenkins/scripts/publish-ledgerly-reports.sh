#!/usr/bin/env bash
# Regenerate Ledgerly reports and publish to mini_reports/current.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

PKG="${PKG:-artifacts/reports-site}"
REMOTE="${REMOTE_REPORTS_DIR:-/var/www/uat1/mini_reports}"
RELEASE_ID="${RELEASE_ID:-$(date +%Y%m%d%H%M)-ledgerly}"

echo "=== 1. Base reports from current release ==="
rm -rf "$PKG"
mkdir -p "$PKG"
if [ -d "$REMOTE/current" ]; then
  cp -a "$REMOTE/current/." "$PKG/"
fi

echo "=== 2. Backend Ledgerly unit tests ==="
(cd backend && ./mvnw -q test \
  -Dtest='Ledgerly*Test,ProductDescriptionCodecTest' \
  -Djacoco.skip=true -Dmodernizer.skip=true)

echo "=== 3. Catalog JavaDoc ==="
if [ -d "$PKG/backend/javadoc/catalog/index.html" ]; then
  echo "Keeping existing catalog JavaDoc from current release"
else
  (cd backend && ./mvnw -q -Djacoco.skip=true javadoc:javadoc -DskipTests \
    -Dmaven.javadoc.failOnError=false -Ddoclint=none) || true
fi
if [ -d backend/target/site/apidocs ]; then
  mkdir -p "$PKG/backend/javadoc/catalog"
  cp -a backend/target/site/apidocs/. "$PKG/backend/javadoc/catalog/"
fi

echo "=== 4. Repackage backend unit tests (if surefire exists) ==="
bash jenkins/scripts/package-unit-test-reports.sh "$PKG" || true

echo "=== 5. Skip live Ledgerly API tests ==="
rm -rf "$PKG/backend/ledgerly-live" artifacts/ledgerly-live

rm -rf "$PKG/backend/scenario-integration" "$PKG/backend/ledgerly-scenario-integration" \
  "$PKG/backend/zoho-live" "$PKG/backend/coverage/integration" "$PKG/backend/javadoc/integration"

echo "=== 6. Ledgerly indices ==="
python3 jenkins/scripts/generate-ledgerly-unit-tests-index.py \
  --reports-dir backend/target/surefire-reports \
  --output "$PKG/backend/ledgerly-unit-tests/index.html"
python3 jenkins/scripts/generate-ledgerly-javadoc-index.py \
  --javadocs-dir "$PKG/backend/javadoc" \
  --output "$PKG/backend/javadoc/ledgerly/index.html"
python3 jenkins/scripts/generate-javadocs-index.py \
  --javadocs-dir "$PKG/backend/javadoc" \
  --output "$PKG/backend/javadoc/index.html"

echo "=== 6b. Manual UI tests ==="
bash jenkins/scripts/run-manual-ui-tests.sh || true
mkdir -p "$PKG/manual-ui-tests"
cp -a artifacts/manual-ui-tests/. "$PKG/manual-ui-tests/" 2>/dev/null || true

echo "=== 7. Reports index ==="
python3 jenkins/scripts/generate-reports-site.py --output-dir "$PKG"

echo "=== 8. Publish ==="
RELEASE_PATH="$REMOTE/releases/$RELEASE_ID"
mkdir -p "$REMOTE/releases"
rsync -a --delete "$PKG/" "$RELEASE_PATH/"
ln -sfn "$RELEASE_PATH" "$REMOTE/current"
echo "Published: https://minisnmore.com/reports/ ($RELEASE_ID)"
