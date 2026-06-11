#!/usr/bin/env bash
# Run ON the UAT VPS after JARs are under REPO_ROOT (e.g. from uat-ms-isolated-bundle.tar.gz).
# Starts microservices on 9180–9184; monolith on :8081 is NOT touched.
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-/var/www/uat1/mini_ms_test}"
SCRIPT="$REPO_ROOT/deploy/scripts/uat-microservices-isolated-pm2.sh"

if [ ! -x "$SCRIPT" ]; then
  chmod +x "$REPO_ROOT/deploy/scripts/"*.sh 2>/dev/null || true
fi

if [ ! -f "$SCRIPT" ]; then
  echo "Missing $SCRIPT — extract bundle or set REPO_ROOT." >&2
  exit 1
fi

echo "=== Current monolith (should stay on :8081) ==="
pm2 list 2>/dev/null | grep -E 'mini-uat|8081|NAME' || true
ss -tlnp | grep ':8081' || echo "(nothing on :8081)"

echo ""
echo "=== Deploying isolated microservices (9180–9184) ==="
REPO_ROOT="$REPO_ROOT" bash "$SCRIPT"
