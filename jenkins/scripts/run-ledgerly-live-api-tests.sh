#!/usr/bin/env bash
# Live Ledgerly integration tests on UAT + HTML report under artifacts/ledgerly-live/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

export API_TEST_BASE_URL="${API_TEST_BASE_URL:-http://127.0.0.1:9180}"
mkdir -p artifacts/ledgerly-live

python3 deploy/postman/run-ledgerly-live-api-tests.py

echo "Ledgerly live report: artifacts/ledgerly-live/index.html"
