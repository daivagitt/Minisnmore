#!/usr/bin/env bash
# Full OpenAPI lifecycle API tests (all ~170 operations, 2xx gate).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

export API_TEST_BASE_URL="${API_TEST_BASE_URL:-https://minisnmore.com}"
export API_LIFECYCLE_REPORT="${API_LIFECYCLE_REPORT:-artifacts/api-tests/lifecycle-report.json}"
export API_LIFECYCLE_ALLOW_FAILURES="${API_LIFECYCLE_ALLOW_FAILURES:-false}"

mkdir -p artifacts/api-tests

export API_OPENAPI_URL="${API_OPENAPI_URL:-${API_TEST_BASE_URL}/v3/api-docs}"

echo "=== API lifecycle tests (all OpenAPI endpoints) → ${API_TEST_BASE_URL} ==="
echo "OpenAPI spec: ${API_OPENAPI_URL}"
python3 deploy/postman/run-lifecycle-api-tests.py

python3 jenkins/scripts/lifecycle-report-to-junit.py \
  --input "$API_LIFECYCLE_REPORT" \
  --output artifacts/api-tests/lifecycle-junit.xml \
  --suite-name "API lifecycle (all endpoints)"

python3 jenkins/scripts/generate-lifecycle-api-report.py \
  --input "$API_LIFECYCLE_REPORT" \
  --output artifacts/api-tests/lifecycle-report.html

echo "Lifecycle report: ${API_LIFECYCLE_REPORT}"
echo "HTML report: artifacts/api-tests/lifecycle-report.html"
echo "JUnit: artifacts/api-tests/lifecycle-junit.xml"
