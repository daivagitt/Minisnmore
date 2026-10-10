#!/usr/bin/env bash
# End-to-end positive/negative scenario suite (all modules) → artifacts/e2e-scenarios/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export API_TEST_BASE_URL="${API_TEST_BASE_URL:-http://127.0.0.1:9180}"
export FRONTEND_BASE_URL="${FRONTEND_BASE_URL:-http://127.0.0.1:3000}"
export API_TEST_NO_MAIL="${API_TEST_NO_MAIL:-true}"

mkdir -p "$ROOT/artifacts/e2e-scenarios"
python3 "$ROOT/deploy/postman/run-e2e-scenario-tests.py"
echo "E2E scenario report: artifacts/e2e-scenarios/index.html"
