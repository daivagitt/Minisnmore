#!/usr/bin/env bash
# Encrypt a host env file at rest (AES-256-CBC + PBKDF2).
# Usage:
#   ./deploy/scripts/env-file-encrypt.sh /etc/mini-and-more/prod.env
#   KEEP_PLAINTEXT=1 ./deploy/scripts/env-file-encrypt.sh /etc/mini-and-more/uat.env
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/encrypted-env.sh
source "${SCRIPT_DIR}/lib/encrypted-env.sh"

PLAIN="${1:-}"
if [ -z "$PLAIN" ] || [ ! -f "$PLAIN" ]; then
  echo "Usage: $0 /etc/mini-and-more/prod.env" >&2
  exit 1
fi

REMOVE=yes
if [ "${KEEP_PLAINTEXT:-0}" = "1" ]; then
  REMOVE=no
fi
mini_encrypt_env_file "$PLAIN" "$REMOVE"
