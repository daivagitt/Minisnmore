#!/usr/bin/env bash
# Jenkins entry: Postman CLI smoke + performance (10/25 users) + full lifecycle API tests.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

export API_TEST_BASE_URL="${API_TEST_BASE_URL:-https://uat.minisnmore.com}"

if [[ "${RUN_API_SMOKE_TESTS:-true}" == "true" || "${RUN_POSTMAN_PERFORMANCE:-true}" == "true" ]]; then
  bash jenkins/scripts/run-postman-ci.sh
fi

if [[ "${RUN_API_LIFECYCLE_TESTS:-true}" == "true" ]]; then
  bash jenkins/scripts/run-api-lifecycle-ci.sh
fi

if [[ "${RUN_K6_LOAD_TESTS:-false}" == "true" ]]; then
  bash jenkins/scripts/run-k6-load-tests.sh
fi

if [[ "${RUN_API_SMOKE_TESTS:-true}" == "true" || "${RUN_POSTMAN_PERFORMANCE:-true}" == "true" || "${RUN_K6_LOAD_TESTS:-false}" == "true" ]]; then
  python3 jenkins/scripts/generate-postman-ci-report.py \
    --output artifacts/postman/index.html \
    --base-url "${API_TEST_BASE_URL}" \
    --perf-max-ms "${PERF_MAX_RESPONSE_MS:-3000}" \
    --perf-max-ms-25 "${PERF_MAX_RESPONSE_MS_25:-6000}" \
    --load-users "${POSTMAN_LOAD_USERS:-10,25}" \
    --k6-vus-levels "${K6_VUS_LEVELS:-10,25}"
  echo "API test report: artifacts/postman/index.html"
fi

python3 jenkins/scripts/generate-api-ci-dashboard.py --output artifacts/api-tests/index.html 2>/dev/null || true
