#!/usr/bin/env bash
# Build branded frontend + consolidated backend unit-test HTML summaries.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

package_dir="${1:-artifacts/reports-site}"
mkdir -p "$package_dir"

if [ -f frontend/coverage/junit.xml ]; then
  mkdir -p "$package_dir/frontend/unit-tests"
  python3 jenkins/scripts/generate-unit-test-report.py \
    --kind frontend \
    --input frontend/coverage/junit.xml \
    --output "$package_dir/frontend/unit-tests/index.html" \
    --title "Frontend unit tests" \
    --coverage-href "../coverage-summary/index.html"
  mkdir -p "$package_dir/frontend/unit-tests/scenarios"
  python3 jenkins/scripts/generate-frontend-unit-scenario-report.py \
    --input frontend/coverage/junit.xml \
    --output-dir "$package_dir/frontend/unit-tests/scenarios"
fi

JAVA_MODULES=(
  "services/gateway:Gateway"
  "backend:Catalog"
  "services/store:Store"
  "services/commerce:Commerce"
)

module_flags=()
for spec in "${JAVA_MODULES[@]}"; do
  IFS=: read -r module_dir display_name <<< "$spec"
  reports_dir="${module_dir}/target/surefire-reports"
  if [ -d "$reports_dir" ] && [ -n "$(find "$reports_dir" -maxdepth 1 -name 'TEST-*.xml' 2>/dev/null | head -1)" ]; then
    module_flags+=( --module "${reports_dir}|${display_name}" )
  fi
done

if [ "${#module_flags[@]}" -gt 0 ]; then
  mkdir -p "$package_dir/backend/unit-tests"
  python3 jenkins/scripts/generate-unit-test-report.py \
    --kind backend \
    "${module_flags[@]}" \
    --output "$package_dir/backend/unit-tests/index.html" \
    --title "Backend unit tests" \
    --coverage-href "../coverage-summary/index.html"
fi
