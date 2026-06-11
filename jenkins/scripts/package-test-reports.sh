#!/usr/bin/env bash
# Branded unit-test and code-coverage HTML for Jenkins artifacts and reports site.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
package_dir="${1:-artifacts/reports-site}"

bash "$ROOT/jenkins/scripts/package-unit-test-reports.sh" "$package_dir"
bash "$ROOT/jenkins/scripts/package-coverage-reports.sh" "$package_dir"
