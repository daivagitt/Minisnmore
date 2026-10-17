#!/usr/bin/env bash
# Import secrets from a host env file into app_vault_secret via catalog admin API.
# Usage:
#   ADMIN_JWT=eyJ... BASE_URL=https://uat.minisnmore.com ./deploy/scripts/vault-import-env.sh /etc/mini-and-more/uat.env
set -euo pipefail

ENV_FILE="${1:-}"
BASE_URL="${BASE_URL:-http://127.0.0.1:8080}"
ADMIN_JWT="${ADMIN_JWT:-}"
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/encrypted-env.sh
source "${SCRIPT_DIR}/lib/encrypted-env.sh"

if [[ -z "$ENV_FILE" ]]; then
  echo "Usage: ADMIN_JWT=<token> $0 /path/to/prod.env[.enc]" >&2
  exit 1
fi
if [[ -z "$ADMIN_JWT" ]]; then
  echo "ADMIN_JWT is required (admin bearer token)." >&2
  exit 1
fi

TMP_ENV=""
cleanup_import_tmp() {
  if [[ -n "${TMP_ENV:-}" && -f "$TMP_ENV" ]]; then
    shred -u "$TMP_ENV" 2>/dev/null || rm -f "$TMP_ENV"
  fi
}
trap cleanup_import_tmp EXIT

READ_FILE="$ENV_FILE"
if [[ "$ENV_FILE" == *.enc ]]; then
  TMP_ENV="$(mktemp "${TMPDIR:-/tmp}/vault-import.XXXXXX")"
  mini_env_openssl_decrypt_to "$ENV_FILE" "$TMP_ENV"
  READ_FILE="$TMP_ENV"
elif [[ ! -f "$ENV_FILE" ]]; then
  echo "ERROR: missing $ENV_FILE" >&2
  exit 1
fi

SKIP_PREFIXES='^(VAULT_MASTER_KEY|SPRING_DATASOURCE_|SPRING_R2DBC_|SPRING_LIQUIBASE_|VAULT_JDBC_)'
payload='{'
first=1
while IFS= read -r line || [[ -n "$line" ]]; do
  line="${line%%#*}"
  line="$(echo "$line" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  [[ -z "$line" ]] && continue
  [[ "$line" != *=* ]] && continue
  key="${line%%=*}"
  val="${line#*=}"
  key="$(echo "$key" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  val="$(echo "$val" | sed 's/^[[:space:]]*//;s/[[:space:]]*$//')"
  val="${val%\"}"; val="${val#\"}"
  [[ -z "$val" ]] && continue
  if echo "$key" | grep -Eq "$SKIP_PREFIXES"; then
    continue
  fi
  esc_key=$(printf '%s' "$key" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')
  esc_val=$(printf '%s' "$val" | python3 -c 'import json,sys; print(json.dumps(sys.stdin.read()))')
  if [[ $first -eq 1 ]]; then first=0; else payload+=','; fi
  payload+="${esc_key}:${esc_val}"
done < "$READ_FILE"
payload+='}'

curl -fsS -X POST "${BASE_URL}/api/v1/admin/vault/import" \
  -H "Authorization: Bearer ${ADMIN_JWT}" \
  -H "Content-Type: application/json" \
  -d "$payload"

echo ""
echo "Vault import complete."
