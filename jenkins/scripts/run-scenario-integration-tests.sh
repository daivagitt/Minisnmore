#!/usr/bin/env bash
# Scenario-based production integration tests → artifacts/scenario-integration/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export API_TEST_BASE_URL="${API_TEST_BASE_URL:-https://minisnmore.com}"

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi

mkdir -p "$ROOT/artifacts/scenario-integration"
python3 "$ROOT/deploy/postman/run-scenario-integration-tests.py"
echo "Scenario integration report: artifacts/scenario-integration/index.html"
