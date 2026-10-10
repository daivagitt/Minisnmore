#!/usr/bin/env bash
# Render frontend/backend unit-test + coverage HTML from qa-run-summary rollup (our report_theme).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

package_dir="${1:-artifacts/reports-site}"
import_dir="${QA_IMPORT_DIR:-artifacts/qa-import}"

python3 jenkins/scripts/materialize-qa-run-unit-coverage.py \
  --summary artifacts/qa-run-summary.json \
  --output-dir "$import_dir"

mkdir -p "$package_dir"

if [ -f "$import_dir/frontend/junit.xml" ]; then
  mkdir -p "$package_dir/frontend/unit-tests"
  python3 jenkins/scripts/generate-unit-test-report.py \
    --kind frontend \
    --input "$import_dir/frontend/junit.xml" \
    --output "$package_dir/frontend/unit-tests/index.html" \
    --title "Frontend unit tests" \
    --coverage-href "../coverage-summary/index.html"
fi

if [ -f "$import_dir/frontend/coverage-summary.json" ]; then
  mkdir -p "$package_dir/frontend/coverage-summary"
  python3 jenkins/scripts/generate-coverage-report.py \
    --kind frontend \
    --input "$import_dir/frontend/coverage-summary.json" \
    --detail-href ../coverage/index.html \
    --line-gate 95 \
    --branch-gate 92 \
    --output "$package_dir/frontend/coverage-summary/index.html"
fi

module_flags=()
for spec in gateway:Gateway catalog:Catalog store:Store commerce:Commerce; do
  IFS=: read -r slug label <<< "$spec"
  reports_dir="$import_dir/backend/${slug}/surefire-reports"
  if [ -d "$reports_dir" ]; then
    module_flags+=(--module "${reports_dir}|${label}")
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

jacoco_paths=()
for slug in gateway catalog store commerce; do
  jacoco_xml="$import_dir/backend/${slug}/jacoco/jacoco.xml"
  if [ -f "$jacoco_xml" ]; then
    mkdir -p "$package_dir/backend/coverage/${slug}"
    cp -f "$jacoco_xml" "$package_dir/backend/coverage/${slug}/jacoco.xml"
    jacoco_paths+=("$jacoco_xml")
  fi
done

if [ "${#jacoco_paths[@]}" -gt 0 ]; then
  mkdir -p "$package_dir/backend/coverage-summary"
  jacoco_csv="$(IFS=,; echo "${jacoco_paths[*]}")"
  python3 jenkins/scripts/generate-coverage-report.py \
    --kind backend \
    --input "$jacoco_csv" \
    --detail-href ../coverage/index.html \
    --line-gate 95 \
    --branch-gate 70 \
    --output "$package_dir/backend/coverage-summary/index.html"
  python3 jenkins/scripts/generate-backend-coverage-index.py \
    --output-dir "$package_dir/backend/coverage"
fi

echo "QA import unit/coverage HTML: $package_dir/frontend|backend"
