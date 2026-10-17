#!/usr/bin/env bash
# Decrypt host env ciphertext for editing. Re-encrypt when done.
# Usage:
#   ./deploy/scripts/env-file-decrypt.sh /etc/mini-and-more/prod.env.enc
#   ./deploy/scripts/env-file-decrypt.sh /etc/mini-and-more/prod.env.enc /tmp/prod.env.edit
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=lib/encrypted-env.sh
source "${SCRIPT_DIR}/lib/encrypted-env.sh"

ENC="${1:-}"
OUT="${2:-}"
if [ -z "$ENC" ]; then
  echo "Usage: $0 /etc/mini-and-more/prod.env.enc [plaintext-out]" >&2
  exit 1
fi
mini_decrypt_env_file "$ENC" "$OUT"
