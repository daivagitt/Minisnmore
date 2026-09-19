#!/usr/bin/env bash
# Stop legacy UAT processes tied to /var/www/uat1/mini_and_more (monolith + old frontend cwd).
# Does NOT delete files unless REMOVE_DIR=true.
#
# Usage on VPS:
#   bash deploy/scripts/prod-retire-mini-and-more.sh
# Or from laptop:
#   ssh root@93.127.199.55 'bash -s' < deploy/scripts/prod-retire-mini-and-more.sh
set -euo pipefail

LEGACY_DIR="${LEGACY_DIR:-/var/www/uat1/mini_and_more}"
REMOVE_DIR="${REMOVE_DIR:-false}"

echo "=== Stopping legacy PM2 apps (mini_and_more era) ==="
for name in mini-uat mini-prod-frontend mini-prod-gateway mini-prod-catalog mini-prod-catalog-core mini-prod-store mini-prod-commerce mini-prod-integration; do
  if pm2 describe "$name" >/dev/null 2>&1; then
    _cwd=$(pm2 describe "$name" 2>/dev/null | awk '/exec cwd/ {print $4; exit}')
    if [ -n "$_cwd" ] && [[ "$_cwd" == *mini_and_more* ]]; then
      echo "Deleting $name (cwd=$_cwd)"
      pm2 delete "$name" || true
    fi
  fi
done
pm2 save || true

echo ""
echo "Legacy dir: $LEGACY_DIR (still on disk)"
if [ "$REMOVE_DIR" = "true" ] && [ -d "$LEGACY_DIR" ]; then
  if [ -z "$LEGACY_DIR" ] || [ "$LEGACY_DIR" = "/" ] || [[ "$LEGACY_DIR" != /var/www/uat1/* ]]; then
    echo "ERROR: refusing to remove unsafe LEGACY_DIR='$LEGACY_DIR' (expected under /var/www/uat1/)" >&2
    exit 1
  fi
  echo "REMOVE_DIR=true — archiving then removing $LEGACY_DIR"
  tar -czf "/var/www/uat1/mini_and_more-retired-$(date +%Y%m%d).tgz" -C /var/www/uat1 mini_and_more
  rm -rf "$LEGACY_DIR"
  echo "Removed. Archive kept under /var/www/uat1/"
else
  echo "Disk untouched. To archive+delete: REMOVE_DIR=true bash $0"
fi

echo ""
echo "Active stack should be:"
echo "  Frontend:  REPO_ROOT=/var/www/uat1/mini_ms_test  PM2 mini-prod-frontend :3000"
echo "  Gateway:   mini-prod-ms-gateway :9180"
pm2 list 2>/dev/null | grep -E 'mini-uat|NAME' || true
