#!/usr/bin/env bash
# Replace uat3 report *data* with matching outputs from qa-final-report (or another ref).
# Keeps this repo's report HTML generators (report_theme, generate-reports-site.py, etc.).
# Does not import backend/reports/* HTML trees.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REF="${1:-origin/qa-final-report}"
PKG="${PKG:-$ROOT/artifacts/reports-site}"
cd "$ROOT"

extract() {
  local src="$1"
  local dest="$2"
  if ! git cat-file -e "$REF:$src" 2>/dev/null; then
    return 0
  fi
  mkdir -p "$(dirname "$dest")"
  git show "$REF:$src" >"$dest"
  echo "  $dest"
}

echo "=== 1. Sync artifacts/* data files from $REF ==="
while IFS= read -r rel; do
  extract "$rel" "$ROOT/$rel"
done < <(git ls-tree -r --name-only "$REF" artifacts 2>/dev/null | grep -E '\.(json|xml|csv)$' || true)

echo "=== 2. QA rollup (only stored under backend/reports on qa-final-report) ==="
extract backend/reports/qa-run-summary.json "$ROOT/artifacts/qa-run-summary.json"

echo "=== 3. Regenerate branded HTML (this repo's UI, not qa-final-report pages) ==="
if [ -f "$ROOT/artifacts/api-tests/lifecycle-report.json" ]; then
  python3 jenkins/scripts/generate-lifecycle-api-report.py \
    --input "$ROOT/artifacts/api-tests/lifecycle-report.json" \
    --output "$ROOT/artifacts/api-tests/lifecycle-report.html"
fi
python3 jenkins/scripts/generate-api-ci-dashboard.py \
  --output "$ROOT/artifacts/api-tests/index.html" 2>/dev/null || true

if [ -f "$ROOT/artifacts/sonar-summary/sonar-summary.json" ]; then
  python3 jenkins/scripts/generate-sonar-summary.py \
    --from-json "$ROOT/artifacts/sonar-summary/sonar-summary.json" \
    --output-dir "$ROOT/artifacts/sonar-summary"
fi

if [ -f "$ROOT/artifacts/postman/smoke-report.json" ]; then
  python3 jenkins/scripts/generate-postman-ci-report.py \
    --output "$ROOT/artifacts/postman/index.html" \
    --base-url "${API_TEST_BASE_URL:-https://uat.minisnmore.com}" \
    --perf-max-ms "${PERF_MAX_RESPONSE_MS:-3000}" \
    --perf-max-ms-25 "${PERF_MAX_RESPONSE_MS_25:-6000}" \
    --load-users "${POSTMAN_LOAD_USERS:-15,25,35}" \
    --k6-vus-levels "${K6_VUS_LEVELS:-10,15,25,35}"
fi

if [ -f "$ROOT/artifacts/manual-ui-tests/manual-ui-tests-report.json" ]; then
  python3 jenkins/scripts/run-manual-ui-tests.py --from-json || true
fi

if [ -f "$ROOT/artifacts/e2e-scenarios/e2e-scenarios-report.json" ]; then
  python3 "$ROOT/deploy/postman/run-e2e-scenario-tests.py" --from-json 2>/dev/null || true
fi

echo "=== 4. Frontend/backend unit tests + coverage (rollup from qa-run-summary.json) ==="
if [ -f "$ROOT/artifacts/qa-run-summary.json" ]; then
  bash jenkins/scripts/package-qa-import-unit-coverage.sh "$PKG"
else
  echo "  (skip: artifacts/qa-run-summary.json missing)"
fi

echo "=== 5. Push matching data into hosted reports package (if present) ==="
if [ -d "$PKG" ]; then
  mkdir -p \
    "$PKG/backend/api" \
    "$PKG/backend/postman" \
    "$PKG/sonar-summary" \
    "$PKG/manual-ui-tests" \
    "$PKG/backend/e2e-scenarios"

  if [ -d "$ROOT/artifacts/api-tests" ]; then
    cp -a "$ROOT/artifacts/api-tests/." "$PKG/backend/api/"
  fi
  for f in smoke-report.json smoke-junit.xml index.html; do
    [ -f "$ROOT/artifacts/postman/$f" ] && cp -f "$ROOT/artifacts/postman/$f" "$PKG/backend/postman/$f"
  done
  if [ -d "$ROOT/artifacts/sonar-summary" ]; then
    cp -a "$ROOT/artifacts/sonar-summary/." "$PKG/sonar-summary/"
  fi
  if [ -d "$ROOT/artifacts/manual-ui-tests" ]; then
    cp -a "$ROOT/artifacts/manual-ui-tests/." "$PKG/manual-ui-tests/" 2>/dev/null || true
  fi
  if [ -d "$ROOT/artifacts/e2e-scenarios" ]; then
    cp -a "$ROOT/artifacts/e2e-scenarios/." "$PKG/backend/e2e-scenarios/"
  fi
  if [ -f "$ROOT/artifacts/qa-run-summary.json" ]; then
    cp -f "$ROOT/artifacts/qa-run-summary.json" "$PKG/qa-run-summary.json"
  fi

  python3 jenkins/scripts/generate-reports-site.py --output-dir "$PKG"
  bash jenkins/scripts/package-qa-dashboard.sh "$PKG"
  echo "  Updated $PKG (index + engineering QA dashboard)"
else
  echo "  (skip: $PKG not found — run publish after package-test-reports.sh)"
fi

echo "=== Done ==="
