#!/usr/bin/env bash
# Reload /etc/mini-and-more/prod.env into one or all PM2 microservices.
#
# Context: A plain `pm2 restart <service>` does NOT pick up updated env variables.
# You must source the env file AND pass --update-env for new values to take effect.
#
# Usage:
#   # Reload ALL isolated microservices:
#   bash deploy/scripts/prod-reload-env-and-restart.sh
#
#   # Reload a single service (e.g. after updating LEDGERLY_API_TOKEN):
#   bash deploy/scripts/prod-reload-env-and-restart.sh mini-prod-ms-catalog-core
#
#   # Multiple specific services:
#   bash deploy/scripts/prod-reload-env-and-restart.sh mini-prod-ms-catalog-core mini-prod-ms-commerce
#
set -euo pipefail

export PATH="/usr/local/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

ENV_FILE="${PROD_ENV_FILE:-/etc/mini-and-more/prod.env}"

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
  mini-prod-ms-catalog-core
  mini-prod-ms-gateway
  mini-prod-ms-store
  mini-prod-ms-commerce
  mini-prod-ms-integration
)

legacy_pm2_ms_name() {
  case "$1" in
    mini-prod-ms-*) printf '%s\n' "mini-uat-ms-${1#mini-prod-ms-}" ;;
    mini-prod-frontend) printf '%s\n' "mini-uat-frontend" ;;
    *) printf '%s\n' "" ;;
  esac
}

restart_one() {
  local svc="$1"
  local live="$svc"
  local legacy
  if ! pm2 describe "$svc" >/dev/null 2>&1; then
    legacy="$(legacy_pm2_ms_name "$svc")"
    if [ -n "$legacy" ] && pm2 describe "$legacy" >/dev/null 2>&1; then
      live="$legacy"
    else
      echo "  SKIP: $svc is not registered in PM2 (run prod-microservices-isolated-pm2.sh to start it)"
      return 0
    fi
  fi
  echo "Restarting $live with --update-env..."
  pm2 restart "$live" --update-env
  echo "  Done: $live"
}

if [ $# -gt 0 ]; then
  SERVICES=("$@")
else
  SERVICES=("${ALL_SERVICES[@]}")
fi

for svc in "${SERVICES[@]}"; do
  restart_one "$svc"
done

pm2 save
echo ""
echo "Env reload complete. Check logs with: pm2 logs <service> --lines 20"
