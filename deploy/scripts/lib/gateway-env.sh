# shellcheck shell=bash
# Shared gateway env for the production isolated stack.
# Prefer: source this file, then load_mini_env (supports encrypted prod.env.enc).
#
# Partial SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_N_* env vars override YAML route[0]
# with uri=null and predicates=[] → gateway crash loop. Never set route URIs via env; use MINIANDMORE_*_URI.

_GATEWAY_ENV_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=encrypted-env.sh
source "${_GATEWAY_ENV_DIR}/encrypted-env.sh"

gateway_sanitize_env() {
  local _var _removed=0

  while IFS= read -r _var; do
    [ -z "$_var" ] && continue
    unset "$_var"
    _removed=$((_removed + 1))
  done < <(compgen -e | grep -E '^SPRING_CLOUD_GATEWAY(_SERVER_WEBFLUX)?(_ROUTES|\.ROUTES)' || true)

  if [ "$_removed" -gt 0 ]; then
    echo "WARN: unset ${_removed} SPRING_CLOUD_GATEWAY route env var(s) — use MINIANDMORE_*_URI in YAML only." >&2
  fi

  local _store_port="${STORE_PORT:-9181}"
  local _commerce_port="${COMMERCE_PORT:-9182}"
  local _integration_port="${INTEGRATION_PORT:-9183}"

  # Bash ${VAR:-x} does not help once exported empty from prod.env — force non-empty URIs.
  if [ -z "${MINIANDMORE_STORE_URI:-}" ]; then
    export MINIANDMORE_STORE_URI="http://127.0.0.1:${_store_port}"
  fi
  if [ -z "${MINIANDMORE_COMMERCE_URI:-}" ]; then
    export MINIANDMORE_COMMERCE_URI="http://127.0.0.1:${_commerce_port}"
  fi
  if [ -z "${MINIANDMORE_INTEGRATION_URI:-}" ]; then
    export MINIANDMORE_INTEGRATION_URI="http://127.0.0.1:${_integration_port}"
  fi
  export GATEWAY_RESPONSE_TIMEOUT="${GATEWAY_RESPONSE_TIMEOUT:-600s}"

  gateway_resolve_mail_provider

  # Gmail, Zoho, and most SMTP providers require From to match the authenticated mailbox.
  if [ -z "${JHIPSTER_MAIL_FROM:-}" ] && [ -n "${SPRING_MAIL_USERNAME:-}" ]; then
    export JHIPSTER_MAIL_FROM="${SPRING_MAIL_USERNAME}"
  fi
}

# Select active SMTP provider without removing alternate credentials from prod.env.
# SPRING_MAIL_PROVIDER: gmail (default) | zoho
# Copies GMAIL_MAIL_* or ZOHO_MAIL_* onto SPRING_MAIL_* so leftover values from the
# other provider (common when prod.env sets SPRING_MAIL_* to Zoho) cannot leak through.
gateway_resolve_mail_provider() {
  local _provider
  _provider="$(printf '%s' "${SPRING_MAIL_PROVIDER:-gmail}" | tr '[:upper:]' '[:lower:]')"

  case "$_provider" in
    zoho)
      if [ -z "${ZOHO_MAIL_HOST:-}" ] || [ -z "${ZOHO_MAIL_USERNAME:-}" ] || [ -z "${ZOHO_MAIL_PASSWORD:-}" ]; then
        echo "ERROR: SPRING_MAIL_PROVIDER=zoho but ZOHO_MAIL_HOST/USERNAME/PASSWORD not set." >&2
        return 1
      fi
      export SPRING_MAIL_HOST="${ZOHO_MAIL_HOST}"
      export SPRING_MAIL_PORT="${ZOHO_MAIL_PORT:-587}"
      export SPRING_MAIL_USERNAME="${ZOHO_MAIL_USERNAME}"
      export SPRING_MAIL_PASSWORD="${ZOHO_MAIL_PASSWORD}"
      if [ -n "${ZOHO_MAIL_FROM:-}" ]; then
        export JHIPSTER_MAIL_FROM="${ZOHO_MAIL_FROM}"
      elif [ -n "${ZOHO_MAIL_USERNAME:-}" ]; then
        export JHIPSTER_MAIL_FROM="${ZOHO_MAIL_USERNAME}"
      fi
      ;;
    gmail)
      if [ -n "${GMAIL_MAIL_HOST:-}" ] && [ -n "${GMAIL_MAIL_USERNAME:-}" ] && [ -n "${GMAIL_MAIL_PASSWORD:-}" ]; then
        export SPRING_MAIL_HOST="${GMAIL_MAIL_HOST}"
        export SPRING_MAIL_PORT="${GMAIL_MAIL_PORT:-587}"
        export SPRING_MAIL_USERNAME="${GMAIL_MAIL_USERNAME}"
        export SPRING_MAIL_PASSWORD="${GMAIL_MAIL_PASSWORD}"
        if [ -n "${GMAIL_MAIL_FROM:-}" ]; then
          export JHIPSTER_MAIL_FROM="${GMAIL_MAIL_FROM}"
        else
          export JHIPSTER_MAIL_FROM="${GMAIL_MAIL_USERNAME}"
        fi
      elif [ -z "${SPRING_MAIL_HOST:-}" ] || [ -z "${SPRING_MAIL_USERNAME:-}" ] || [ -z "${SPRING_MAIL_PASSWORD:-}" ]; then
        echo "ERROR: SPRING_MAIL_PROVIDER=gmail but GMAIL_MAIL_* / SPRING_MAIL_HOST/USERNAME/PASSWORD not set." >&2
        return 1
      fi
      ;;
    *)
      echo "ERROR: SPRING_MAIL_PROVIDER must be gmail or zoho (got: ${_provider})." >&2
      return 1
      ;;
  esac
}

# Loads /etc/mini-and-more/prod.env.enc (preferred) or plaintext prod.env.
# Override with PROD_ENV_WHICH=uat for UAT hosts.
load_mini_env() {
  local which="${PROD_ENV_WHICH:-prod}"
  if [ -n "${PROD_ENV_FILE:-}" ]; then
    # Explicit path: if .enc, decrypt; else source plaintext
    if [[ "$PROD_ENV_FILE" == *.enc ]]; then
      local tmp
      tmp="$(mktemp "${TMPDIR:-/tmp}/mini-env.XXXXXX")"
      mini_env_openssl_decrypt_to "$PROD_ENV_FILE" "$tmp" || {
        rm -f "$tmp"
        return 1
      }
      set -a
      # shellcheck source=/dev/null
      . "$tmp"
      set +a
      shred -u "$tmp" 2>/dev/null || rm -f "$tmp"
      return 0
    fi
    if [ ! -f "$PROD_ENV_FILE" ]; then
      echo "ERROR: missing $PROD_ENV_FILE" >&2
      return 1
    fi
    set -a
    # shellcheck source=/dev/null
    . "$PROD_ENV_FILE"
    set +a
    return 0
  fi
  mini_load_host_env "$which"
}

legacy_pm2_ms_name() {
  case "$1" in
    mini-prod-ms-*) printf '%s\n' "mini-uat-ms-${1#mini-prod-ms-}" ;;
    mini-prod-frontend) printf '%s\n' "mini-uat-frontend" ;;
    *) printf '%s\n' "" ;;
  esac
}

pm2_delete_ms() {
  local name="$1"
  local legacy
  pm2 delete "$name" >/dev/null 2>&1 || true
  legacy="$(legacy_pm2_ms_name "$name")"
  if [ -n "$legacy" ]; then
    pm2 delete "$legacy" >/dev/null 2>&1 || true
  fi
}

pm2_live_ms_name() {
  local name="$1"
  local legacy
  if pm2 describe "$name" >/dev/null 2>&1; then
    printf '%s\n' "$name"
    return 0
  fi
  legacy="$(legacy_pm2_ms_name "$name")"
  if [ -n "$legacy" ] && pm2 describe "$legacy" >/dev/null 2>&1; then
    printf '%s\n' "$legacy"
    return 0
  fi
  return 1
}

gateway_verify_rate_limit_redis() {
  local _enabled
  _enabled="$(printf '%s' "${GATEWAY_RATE_LIMIT_ENABLED:-true}" | tr '[:upper:]' '[:lower:]')"
  case "$_enabled" in
    false | 0 | no | off) return 0 ;;
  esac
  local _url="${GATEWAY_RATE_LIMIT_REDIS_URL:-redis://127.0.0.1:6379}"
  if ! command -v redis-cli >/dev/null 2>&1; then
    echo "WARN: redis-cli missing; cannot verify gateway rate-limit Redis at ${_url}" >&2
    return 0
  fi
  if ! redis-cli -u "$_url" ping 2>/dev/null | grep -qF PONG; then
    echo "ERROR: gateway rate limiting is enabled but Redis is unreachable at ${_url}." >&2
    echo "Set GATEWAY_RATE_LIMIT_REDIS_URL (not JHIPSTER_CACHE_REDIS_SERVER) or GATEWAY_RATE_LIMIT_ENABLED=false." >&2
    return 1
  fi
  return 0
}

gateway_verify_env() {
  local _bad
  _bad=$(compgen -e | grep -E '^SPRING_CLOUD_GATEWAY(_SERVER_WEBFLUX)?(_ROUTES|\.ROUTES)' || true)
  if [ -n "$_bad" ]; then
    echo "ERROR: SPRING_CLOUD_GATEWAY route env still set after sanitize:" >&2
    echo "$_bad" >&2
    return 1
  fi
  for _uri in MINIANDMORE_STORE_URI MINIANDMORE_COMMERCE_URI MINIANDMORE_INTEGRATION_URI; do
    if [ -z "${!_uri:-}" ]; then
      echo "ERROR: ${_uri} is empty" >&2
      return 1
    fi
  done
  gateway_verify_rate_limit_redis
}

# UAT restart scripts still use the older function names.
uat_gateway_sanitize_env() { gateway_sanitize_env "$@"; }
uat_gateway_verify_env() { gateway_verify_env "$@"; }
