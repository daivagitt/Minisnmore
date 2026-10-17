# shellcheck shell=bash
# Encrypt / decrypt / load host env files under /etc/mini-and-more/.
#
# Canonical at rest (production):
#   /etc/mini-and-more/prod.env.enc   — AES-256-CBC (PBKDF2) ciphertext
#   /etc/mini-and-more/env.key       — passphrase file (chmod 600) — NOT the vault master key
#
# Plaintext /etc/mini-and-more/prod.env is allowed for migration/editing only.
# Prefer encrypting and removing plaintext after edits.
#
# Usage (from deploy scripts):
#   source deploy/scripts/lib/encrypted-env.sh
#   mini_load_host_env prod   # or: uat
#
# Encrypt:
#   ./deploy/scripts/env-file-encrypt.sh /etc/mini-and-more/prod.env
# Decrypt for edit:
#   ./deploy/scripts/env-file-decrypt.sh /etc/mini-and-more/prod.env.enc

mini_env_dir() {
  printf '%s\n' "${MINI_ENV_DIR:-/etc/mini-and-more}"
}

mini_env_key_file() {
  printf '%s\n' "${MINI_ENV_KEY_FILE:-$(mini_env_dir)/env.key}"
}

mini_env_openssl_encrypt() {
  local plaintext="$1"
  local ciphertext="$2"
  local keyfile="${3:-$(mini_env_key_file)}"
  openssl enc -aes-256-cbc -pbkdf2 -iter 600000 -salt \
    -in "$plaintext" -out "$ciphertext" -pass "file:${keyfile}"
}

mini_env_openssl_decrypt_to() {
  local ciphertext="$1"
  local out="$2"
  local keyfile="${3:-$(mini_env_key_file)}"
  openssl enc -d -aes-256-cbc -pbkdf2 -iter 600000 \
    -in "$ciphertext" -out "$out" -pass "file:${keyfile}"
}

mini_ensure_env_key() {
  local keyfile="${1:-$(mini_env_key_file)}"
  if [ -f "$keyfile" ]; then
    return 0
  fi
  mkdir -p "$(dirname "$keyfile")"
  umask 077
  openssl rand -base64 48 >"$keyfile"
  chmod 600 "$keyfile"
  echo "Created new env file key: $keyfile (chmod 600). Back this up offline." >&2
}

# Encrypt plaintext path → same path with .enc; optionally remove plaintext (default yes).
mini_encrypt_env_file() {
  local plaintext="${1:?plaintext env path required}"
  local remove_plain="${2:-yes}"
  local ciphertext="${plaintext%.env}.env.enc"
  if [[ "$plaintext" == *.env.enc ]]; then
    echo "ERROR: already looks encrypted: $plaintext" >&2
    return 1
  fi
  if [ ! -f "$plaintext" ]; then
    echo "ERROR: missing $plaintext" >&2
    return 1
  fi
  mini_ensure_env_key
  umask 077
  mini_env_openssl_encrypt "$plaintext" "$ciphertext"
  chmod 600 "$ciphertext"
  if [ "$remove_plain" = "yes" ]; then
    shred -u "$plaintext" 2>/dev/null || rm -f "$plaintext"
    echo "Encrypted → $ciphertext (plaintext removed)." >&2
  else
    echo "Encrypted → $ciphertext (plaintext kept)." >&2
  fi
}

# Decrypt .enc → plaintext path (for editing). Does not delete .enc.
mini_decrypt_env_file() {
  local ciphertext="${1:?ciphertext path required}"
  local plaintext="${2:-}"
  if [ -z "$plaintext" ]; then
    if [[ "$ciphertext" == *.env.enc ]]; then
      plaintext="${ciphertext%.enc}"
    else
      plaintext="${ciphertext}.decoded"
    fi
  fi
  if [ ! -f "$ciphertext" ]; then
    echo "ERROR: missing $ciphertext" >&2
    return 1
  fi
  if [ ! -f "$(mini_env_key_file)" ]; then
    echo "ERROR: missing key $(mini_env_key_file)" >&2
    return 1
  fi
  umask 077
  mini_env_openssl_decrypt_to "$ciphertext" "$plaintext"
  chmod 600 "$plaintext"
  echo "Decrypted → $plaintext (edit, then re-run env-file-encrypt.sh)." >&2
}

# Source host env for prod|uat. Prefers .enc when present.
mini_load_host_env() {
  local which="${1:-prod}"
  local base enc tmp
  base="$(mini_env_dir)/${which}.env"
  enc="${base}.enc"
  tmp=""

  cleanup_tmp() {
    if [ -n "${tmp:-}" ] && [ -f "$tmp" ]; then
      shred -u "$tmp" 2>/dev/null || rm -f "$tmp"
    fi
  }

  if [ -f "$enc" ]; then
    if [ ! -f "$(mini_env_key_file)" ]; then
      echo "ERROR: $enc exists but key missing: $(mini_env_key_file)" >&2
      return 1
    fi
    tmp="$(mktemp "${TMPDIR:-/tmp}/mini-${which}-env.XXXXXX")"
    trap cleanup_tmp RETURN
    if ! mini_env_openssl_decrypt_to "$enc" "$tmp"; then
      echo "ERROR: failed to decrypt $enc (wrong key?)" >&2
      cleanup_tmp
      return 1
    fi
    chmod 600 "$tmp"
    set -a
    # shellcheck source=/dev/null
    . "$tmp"
    set +a
    cleanup_tmp
    trap - RETURN
    return 0
  fi

  if [ -f "$base" ]; then
    echo "WARN: loading plaintext $base — encrypt with deploy/scripts/env-file-encrypt.sh" >&2
    set -a
    # shellcheck source=/dev/null
    . "$base"
    set +a
    return 0
  fi

  echo "ERROR: missing $enc (or plaintext $base). See deploy/env/${which}.env.example" >&2
  return 1
}
