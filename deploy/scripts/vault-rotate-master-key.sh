#!/usr/bin/env bash
# Re-encrypt app_vault_secret + Zoho columns with a new VAULT_MASTER_KEY (catalog must be up with OLD key).
# Usage:
#   NEW_MASTER_KEY="$(openssl rand -base64 32)" \
#   ADMIN_JWT=eyJ... BASE_URL=https://uat.minisnmore.com \
#   ./deploy/scripts/vault-rotate-master-key.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
ADMIN_JWT="${ADMIN_JWT:-}"
NEW_MASTER_KEY="${NEW_MASTER_KEY:-}"

if [[ -z "$ADMIN_JWT" ]]; then
  echo "ADMIN_JWT is required." >&2
  exit 1
fi
if [[ -z "$NEW_MASTER_KEY" ]]; then
  echo "NEW_MASTER_KEY is required (openssl rand -base64 32)." >&2
  exit 1
fi

esc=$(printf '%s' "$NEW_MASTER_KEY" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')

curl -fsS -X POST "${BASE_URL}/api/v1/admin/vault/rotate-master-key" \
  -H "Authorization: Bearer ${ADMIN_JWT}" \
  -H "Content-Type: application/json" \
  -d "{\"newMasterKey\":${esc}}"

echo ""
echo "Rotation complete in DB. Update VAULT_MASTER_KEY on all hosts to the new value and restart all Java services."
