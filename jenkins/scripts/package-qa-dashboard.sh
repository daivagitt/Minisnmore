#!/usr/bin/env bash
# Publish feat/testing-coverage engineering QA dashboard for the hosted reports site.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

package_dir="${1:-artifacts/reports-site}"
out_dir="$package_dir/engineering-qa"
mkdir -p "$out_dir"

python3 jenkins/scripts/generate-qa-dashboard.py \
  --template "$ROOT/report.html" \
  --output "$out_dir/index.html"

# Dashboard references daiva logo; reuse reports-site asset.
if [ -f "$package_dir/daiva-logo.svg" ]; then
  cp -f "$package_dir/daiva-logo.svg" "$out_dir/daiva-logo.svg"
elif [ -f jenkins/assets/daiva-technologies-logo.svg ]; then
  cp -f jenkins/assets/daiva-technologies-logo.svg "$out_dir/daiva-logo.svg"
fi

echo "QA dashboard: $out_dir/index.html"
