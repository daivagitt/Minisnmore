#!/usr/bin/env bash
# Build branded frontend + consolidated backend JaCoCo coverage summaries.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

package_dir="${1:-artifacts/reports-site}"
mkdir -p "$package_dir"

if [ -d coverage/frontend ]; then
  mkdir -p "$package_dir/frontend/coverage"
  cp -R coverage/frontend/. "$package_dir/frontend/coverage/"
fi

if [ -f coverage/frontend/coverage-summary.json ]; then
  mkdir -p "$package_dir/frontend/coverage-summary"
  python3 jenkins/scripts/generate-coverage-report.py \
    --kind frontend \
    --input coverage/frontend/coverage-summary.json \
    --detail-href ../coverage/index.html \
    --line-gate 95 \
    --branch-gate 88 \
    --output "$package_dir/frontend/coverage-summary/index.html"
fi

collect_jacoco_module() {
  local module_dir="$1"
  local slug="$2"
  local display_name="$3"

  local jacoco_dir="${module_dir}/target/site/jacoco"
  local jacoco_xml=""
  if [ ! -d "$jacoco_dir" ]; then
    return 0
  fi
  if [ -f "$jacoco_dir/jacoco.xml" ]; then
    jacoco_xml="$jacoco_dir/jacoco.xml"
  else
    jacoco_xml="$(find "$jacoco_dir" -maxdepth 2 -name 'jacoco.xml' 2>/dev/null | head -n 1)"
  fi
  if [ -z "$jacoco_xml" ]; then
    return 0
  fi

  mkdir -p "$package_dir/backend/coverage/${slug}"
  cp -R "$jacoco_dir/." "$package_dir/backend/coverage/${slug}/"
  echo "${jacoco_xml}|${display_name}"
}

module_args=()
while IFS= read -r line; do
  [ -n "$line" ] && module_args+=( "$line" )
done < <(
  collect_jacoco_module gateway gateway Gateway
  collect_jacoco_module backend catalog "Catalog"
  collect_jacoco_module store store Store
  collect_jacoco_module commerce commerce Commerce
)

if [ "${#module_args[@]}" -gt 0 ]; then
  mkdir -p "$package_dir/backend/coverage-summary"
  jacoco_csv="$(printf '%s\n' "${module_args[@]}" | cut -d'|' -f1 | paste -sd,)"
  python3 jenkins/scripts/generate-coverage-report.py \
    --kind backend \
    --input "$jacoco_csv" \
    --detail-href ../coverage/index.html \
    --line-gate 90 \
    --branch-gate 70 \
    --output "$package_dir/backend/coverage-summary/index.html"

  python3 jenkins/scripts/generate-backend-coverage-index.py \
    --output-dir "$package_dir/backend/coverage"
fi
