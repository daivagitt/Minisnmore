#!/usr/bin/env bash
# OAuth diagnostics for production (run on VPS as root).
set -euo pipefail
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

HOST="${OAUTH_HOST:-https://minisnmore.com}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
NGINX_SITE="${NGINX_SITE:-/etc/nginx/sites-available/mini-prod.conf}"

echo "=== Nginx OAuth routes (must proxy to :${GATEWAY_PORT}) ==="
if [ -f "$NGINX_SITE" ]; then
  grep -n 'oauth2\|login/oauth2' "$NGINX_SITE" || echo "WARN: no /oauth2/ or /login/oauth2/ in $NGINX_SITE" >&2
else
  echo "WARN: $NGINX_SITE not found" >&2
fi

echo ""
echo "=== Google OAuth start (expect 302 → accounts.google.com) ==="
headers=$(curl -sS -m 15 -D - -o /dev/null "${HOST}/oauth2/authorization/google" || true)
echo "$headers" | head -20
if echo "$headers" | grep -qi 'location:.*accounts\.google\.com'; then
  echo "OK   Google authorization redirect"
else
  echo "FAIL Google did not redirect to accounts.google.com (check gateway + GOOGLE_* env)" >&2
fi
if echo "$headers" | grep -qi 'location:.*redirect_uri=.*login/oauth2/code/google'; then
  echo "OK   redirect_uri includes /login/oauth2/code/google"
elif echo "$headers" | grep -qi 'location:.*redirect_uri=http%3A'; then
  echo "FAIL redirect_uri is http — fix nginx X-Forwarded-Proto https" >&2
fi

echo ""
echo "=== Facebook OAuth start ==="
fb=$(curl -sS -m 15 -D - -o /dev/null "${HOST}/oauth2/authorization/facebook" || true)
echo "$fb" | grep -i '^HTTP\|^location:' | head -5

echo ""
echo "=== Facebook app secret (client_credentials — invalid secret = Meta misconfig) ==="
# shellcheck source=lib/encrypted-env.sh
source "${SCRIPT_DIR}/lib/encrypted-env.sh"
if mini_load_host_env prod; then
  if [ -n "${FACEBOOK_CLIENT_ID:-}" ] && [ -n "${FACEBOOK_CLIENT_SECRET:-}" ]; then
    fb_token=$(
      curl -sS -m 15 \
        "https://graph.facebook.com/v21.0/oauth/access_token?client_id=${FACEBOOK_CLIENT_ID}&client_secret=${FACEBOOK_CLIENT_SECRET}&grant_type=client_credentials" \
        || true
    )
    if echo "$fb_token" | grep -q '"access_token"'; then
      echo "OK   Facebook app id + secret accepted by Graph API"
    else
      echo "FAIL Facebook rejected client credentials:" >&2
      echo "$fb_token" | head -c 300 >&2
      echo "" >&2
      echo "      Fix FACEBOOK_CLIENT_SECRET in host env (Meta → Settings → Basic → App secret)" >&2
    fi
  else
    echo "WARN: FACEBOOK_CLIENT_ID or FACEBOOK_CLIENT_SECRET empty in host env" >&2
  fi
else
  echo "WARN: could not load host env (prod.env.enc / prod.env)" >&2
fi

echo ""
echo "=== Gateway PM2 env (secrets redacted) ==="
GATEWAY_PM2="$(pm2 describe mini-prod-ms-gateway >/dev/null 2>&1 && echo mini-prod-ms-gateway || true)"
if [ -z "$GATEWAY_PM2" ] && pm2 describe mini-uat-ms-gateway >/dev/null 2>&1; then
  GATEWAY_PM2="mini-uat-ms-gateway"
fi
if [ -n "$GATEWAY_PM2" ]; then
  pm2 env "$(pm2 id "$GATEWAY_PM2" 2>/dev/null | head -1)" 2>/dev/null | grep -E 'GOOGLE_CLIENT|FACEBOOK_CLIENT|FRONTEND_AUTH_CALLBACK' | sed 's/SECRET=.*/SECRET=***redacted***/' || true
else
  echo "WARN: production gateway not in PM2" >&2
fi

echo ""
echo "=== Host env OAuth keys (set = present, not values) ==="
for var in GOOGLE_CLIENT_ID GOOGLE_CLIENT_SECRET FACEBOOK_CLIENT_ID FACEBOOK_CLIENT_SECRET FRONTEND_AUTH_CALLBACK_URL; do
  if [ -n "${!var:-}" ]; then
    echo "${var}=***set***"
  else
    echo "${var}=***missing***"
  fi
done

echo ""
echo "=== Local gateway health ==="
curl -fsS -m 5 "http://127.0.0.1:${GATEWAY_PORT}/management/health/liveness" && echo " OK" || echo "FAIL gateway not healthy on :${GATEWAY_PORT}" >&2
