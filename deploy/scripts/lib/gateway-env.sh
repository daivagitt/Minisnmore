# shellcheck shell=bash
# Shared gateway env for UAT isolated stack. Source after /etc/mini-and-more/uat.env.
#
# Partial SPRING_CLOUD_GATEWAY_SERVER_WEBFLUX_ROUTES_N_* env vars override YAML route[0]
# with uri=null and predicates=[] → gateway crash loop. Never set route URIs via env; use MINIANDMORE_*_URI.

uat_gateway_sanitize_env() {
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

  # Bash ${VAR:-x} does not help once exported empty from uat.env — force non-empty URIs.
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

  uat_gateway_resolve_mail_provider

  # Gmail, Zoho, and most SMTP providers require From to match the authenticated mailbox.
  if [ -z "${JHIPSTER_MAIL_FROM:-}" ] && [ -n "${SPRING_MAIL_USERNAME:-}" ]; then
    export JHIPSTER_MAIL_FROM="${SPRING_MAIL_USERNAME}"
  fi
}

# Select active SMTP provider without removing alternate credentials from uat.env.
# SPRING_MAIL_PROVIDER: gmail (default) | zoho
# Copies GMAIL_MAIL_* or ZOHO_MAIL_* onto SPRING_MAIL_* so leftover values from the
# other provider (common when uat.env sets SPRING_MAIL_* to Zoho) cannot leak through.
uat_gateway_resolve_mail_provider() {
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

uat_gateway_verify_env() {
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
  return 0
}
