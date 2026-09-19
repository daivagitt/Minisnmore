#!/usr/bin/env bash
# Ledgerly scenario integration tests → artifacts/ledgerly-scenario-integration/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
export API_TEST_BASE_URL="${API_TEST_BASE_URL:-https://minisnmore.com}"

if [ -f /etc/mini-and-more/prod.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/prod.env
  set +a
fi

mkdir -p "$ROOT/artifacts/ledgerly-scenario-integration"
python3 "$ROOT/deploy/postman/run-ledgerly-scenario-integration-tests.py"
echo "Ledgerly scenario integration report: artifacts/ledgerly-scenario-integration/index.html"
