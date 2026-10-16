#!/usr/bin/env bash
# Exercise deployment path resolution with fake PM2/fuser commands and temporary
# build files. Never reads production environment files or restarts real services.
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
bash "$ROOT/deploy/scripts/ensure-frontend-source.sh"
bash "$ROOT/deploy/scripts/verify-gateway-commerce-route-parity.sh"
TASK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/mini-deploy-layout.XXXXXX")"
trap 'rm -rf "$TASK_DIR"' EXIT
mkdir -p "$TASK_DIR/frontend/dist/client/assets" "$TASK_DIR/frontend/dist/server" "$TASK_DIR/deploy/scripts"
ln -s "$ROOT/frontend/src" "$TASK_DIR/frontend/src"
for file in package.json package-lock.json tsconfig.json vite.config.ts vitest.config.ts; do
  cp "$ROOT/frontend/$file" "$TASK_DIR/frontend/$file"
done
for module in backend services/gateway services/store services/commerce; do
  mkdir -p "$TASK_DIR/$module"
  cp "$ROOT/$module/pom.xml" "$TASK_DIR/$module/pom.xml"
done
cp "$ROOT/deploy/scripts/ensure-frontend-source.sh" "$TASK_DIR/deploy/scripts/"
printf 'layout-check\n' > "$TASK_DIR/frontend/dist/client/RELEASE.txt"
touch "$TASK_DIR/frontend/dist/server/server.js"
export PM2_LAYOUT_LOG="$TASK_DIR/pm2.log"
pm2() { printf '%s\n' "$*" >> "$PM2_LAYOUT_LOG"; }
fuser() { return 0; }
export -f pm2 fuser
REPO_ROOT="$TASK_DIR" SKIP_BUILD=1 NVM_DIR="$TASK_DIR/no-nvm" \
  bash "$ROOT/deploy/scripts/prod-frontend-pm2.sh" > "$TASK_DIR/build-script.log"
REPO_ROOT="$TASK_DIR" NVM_DIR="$TASK_DIR/no-nvm" \
  bash "$ROOT/deploy/scripts/prod-frontend-restart.sh" > "$TASK_DIR/restart-script.log"
python3 - "$TASK_DIR" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
starts = [line for line in (root / 'pm2.log').read_text().splitlines() if line.startswith('start ')]
assert len(starts) == 2, starts
for line in starts:
    assert f'--cwd {root}/frontend ' in line, line
    assert 'run preview' in line, line
print('OK: frontend build/restart helpers start preview from frontend/ (stubbed PM2).')
PY

# Run the laptop deploy helper inside the fake checkout with build/SSH/rsync
# stubs. This validates the embedded remote shell body and transferred paths.
cp "$ROOT/deploy/scripts/prod-deploy-frontend-from-laptop.sh" "$TASK_DIR/deploy/scripts/"
export DEPLOY_LAYOUT_TASK_DIR="$TASK_DIR"
npm() {
  mkdir -p dist/client dist/server
  printf 'WELCOME10\n' > dist/client/app.js
  touch dist/server/server.js
}
ssh() {
  local arg
  for arg in "$@"; do
    if [[ "$arg" == "set -euo pipefail"$'\n'* ]]; then
      printf '%s\n' "$arg" > "$DEPLOY_LAYOUT_TASK_DIR/remote-deploy.sh"
      bash -n "$DEPLOY_LAYOUT_TASK_DIR/remote-deploy.sh"
    fi
  done
  return 0
}
rsync() { printf '%s\n' "$*" >> "$DEPLOY_LAYOUT_TASK_DIR/rsync.log"; }
export -f npm ssh rsync
REMOTE=layout-test.invalid REMOTE_DIR=/tmp/layout-release \
  bash "$TASK_DIR/deploy/scripts/prod-deploy-frontend-from-laptop.sh" > "$TASK_DIR/laptop-script.log"
python3 - "$TASK_DIR" <<'PY'
from pathlib import Path
import sys
root = Path(sys.argv[1])
remote = (root / 'remote-deploy.sh').read_text()
assert 'npm --prefix frontend ci --no-audit --no-fund' in remote
assert '--cwd "$REPO/frontend"' in remote
transfers = (root / 'rsync.log').read_text()
assert f'{root}/frontend/ layout-test.invalid:/tmp/layout-release/frontend/' in transfers
assert '--exclude node_modules' in transfers and "--exclude .env*" in transfers
print('OK: laptop helper transfers the full frontend, installs locked dependencies, and emits valid remote shell (stubbed SSH/rsync).')
PY
