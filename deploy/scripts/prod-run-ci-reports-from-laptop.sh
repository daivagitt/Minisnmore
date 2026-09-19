#!/usr/bin/env bash
# Sync CI sources to UAT, then run full CI + reports on the VPS (tests never run on your Mac).
#
#   bash deploy/scripts/prod-run-ci-reports-from-laptop.sh
#
# Optional env passed to remote script:
#   RUN_API_TESTS=false RUN_JAVADOC=false  # faster smoke
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
REMOTE="${REMOTE:-root@93.127.199.55}"
REMOTE_DIR="${REMOTE_DIR:-/var/www/uat1/mini_ms_test}"

bash "$ROOT/deploy/scripts/prod-sync-ci-sources-from-laptop.sh"

echo ""
echo "=== Running CI on VPS (can take 1–3+ hours on a busy server) ==="
ssh -o ServerAliveInterval=30 "$REMOTE" env \
  REPO_ROOT="${REMOTE_DIR}" \
  RUN_UNIT_TESTS="${RUN_UNIT_TESTS:-}" \
  RUN_API_TESTS="${RUN_API_TESTS:-}" \
  RUN_JAVADOC="${RUN_JAVADOC:-}" \
  RUN_OPENAPI="${RUN_OPENAPI:-}" \
  RUN_SONAR="${RUN_SONAR:-}" \
  PUBLISH_REPORTS="${PUBLISH_REPORTS:-}" \
  CONTINUE_ON_TEST_FAIL="${CONTINUE_ON_TEST_FAIL:-}" \
  API_TEST_BASE_URL="${API_TEST_BASE_URL:-}" \
  bash -lc '
    set -euo pipefail
    export PATH=/usr/local/bin:/usr/bin:/bin:$PATH
    [ -s "$HOME/.nvm/nvm.sh" ] && . "$HOME/.nvm/nvm.sh"
    chmod +x "$REPO_ROOT"/deploy/scripts/*.sh "$REPO_ROOT"/jenkins/scripts/*.sh
    bash "$REPO_ROOT"/deploy/scripts/prod-run-ci-reports-on-server.sh
  '
