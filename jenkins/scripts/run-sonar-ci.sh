#!/usr/bin/env bash
# Run SonarQube analysis for frontend (Vitest) + combined backend, then static summary HTML.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

if [ -f /etc/mini-and-more/sonar-ci.env ]; then
  set -a
  # shellcheck source=/dev/null
  . /etc/mini-and-more/sonar-ci.env
  set +a
fi

SONAR_HOST_URL="${SONAR_HOST_URL:-http://127.0.0.1:9000/sonar}"
SONAR_TOKEN="${SONAR_TOKEN:-}"

if [ -z "$SONAR_TOKEN" ]; then
  echo "WARNING: SONAR_TOKEN not set — skipping Sonar analysis" >&2
  exit 0
fi

status_url="${SONAR_HOST_URL%/}/api/system/status"
if ! curl -fsS "$status_url" >/dev/null 2>&1; then
  echo "WARNING: SonarQube not reachable at $SONAR_HOST_URL — skipping" >&2
  exit 0
fi

echo "=== Sonar: combined backend (mnm-backend) ==="
BACKEND_SONAR_DIR="$ROOT/jenkins/sonar/backend-all"
(
  cd "$BACKEND_SONAR_DIR"
  sonar-scanner \
    -Dsonar.host.url="$SONAR_HOST_URL" \
    -Dsonar.token="$SONAR_TOKEN" \
    -Dsonar.scanner.metadataFilePath="$BACKEND_SONAR_DIR/.scannerwork/report-task.txt"
)

echo "=== Sonar: frontend ==="
if [ -f coverage/frontend/lcov.info ]; then
  sonar-scanner \
    -Dsonar.host.url="$SONAR_HOST_URL" \
    -Dsonar.token="$SONAR_TOKEN" \
    -Dproject.settings=sonar-project.properties
else
  echo "WARNING: coverage/frontend/lcov.info missing — run Vitest coverage first" >&2
fi

OUT_DIR="${SONAR_SUMMARY_DIR:-artifacts/sonar-summary}"
python3 jenkins/scripts/generate-sonar-summary.py \
  --output-dir "$OUT_DIR" \
  --sonar-url "$SONAR_HOST_URL"

echo "Sonar summary: $OUT_DIR/index.html"
