#!/usr/bin/env bash
# Reload /etc/mini-and-more/uat.env into one or all PM2 microservices.
#
# Context: A plain `pm2 restart <service>` does NOT pick up updated env variables.
# You must source the env file AND pass --update-env for new values to take effect.
#
# Usage:
#   # Reload ALL isolated microservices:
#   bash deploy/scripts/uat-reload-env-and-restart.sh
#
#   # Reload a single service (e.g. after updating LEDGERLY_API_TOKEN):
#   bash deploy/scripts/uat-reload-env-and-restart.sh mini-uat-ms-catalog-core
#
#   # Multiple specific services:
#   bash deploy/scripts/uat-reload-env-and-restart.sh mini-uat-ms-catalog-core mini-uat-ms-commerce
#
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

ENV_FILE="${UAT_ENV_FILE:-/etc/mini-and-more/uat.env}"

if [ ! -f "$ENV_FILE" ]; then
  echo "ERROR: $ENV_FILE not found." >&2
  exit 1
fi

set -a
# shellcheck source=/dev/null
. "$ENV_FILE"
set +a

echo "Loaded env from $ENV_FILE"

ALL_SERVICES=(
  mini-uat-ms-catalog-core
  mini-uat-ms-gateway
  mini-uat-ms-store
  mini-uat-ms-commerce
  mini-uat-ms-integration
)

if [ $# -gt 0 ]; then
  SERVICES=("$@")
else
  SERVICES=("${ALL_SERVICES[@]}")
fi

for svc in "${SERVICES[@]}"; do
  if pm2 describe "$svc" >/dev/null 2>&1; then
    echo "Restarting $svc with --update-env..."
    pm2 restart "$svc" --update-env
    echo "  Done: $svc"
  else
    echo "  SKIP: $svc is not registered in PM2 (run uat-microservices-isolated-pm2.sh to start it)"
  fi
done

pm2 save
echo ""
echo "Env reload complete. Check logs with: pm2 logs <service> --lines 20"
