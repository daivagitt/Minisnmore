#!/usr/bin/env bash
# Manual UI positive/negative catalog evaluated against frontend source → artifacts/manual-ui-tests/
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
mkdir -p "$ROOT/artifacts/manual-ui-tests"
python3 "$ROOT/jenkins/scripts/run-manual-ui-tests.py"
echo "Manual UI test report: artifacts/manual-ui-tests/index.html"
