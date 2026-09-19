#!/usr/bin/env bash
exec "$(cd "$(dirname "$0")" && pwd)/deploy/scripts/prod-frontend-pm2.sh" "$@"
