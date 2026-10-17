#!/usr/bin/env bash
# Post-deploy verification for production gateway + Zoho routing (run on VPS).
#   bash deploy/scripts/prod-verify-gateway-stack.sh
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="${REPO_ROOT:-$(cd "${SCRIPT_DIR}/../.." && pwd)}"
# shellcheck source=lib/nginx-prod-site.sh
source "${SCRIPT_DIR}/lib/nginx-prod-site.sh"
# shellcheck source=lib/gateway-env.sh
source "${SCRIPT_DIR}/lib/gateway-env.sh"

GATEWAY_PORT="${GATEWAY_PORT:-9180}"
INTEGRATION_PORT="${INTEGRATION_PORT:-9183}"
ADMIN_EMAIL="${PROD_ADMIN_EMAIL:-admin@minisnmore.com}"
ADMIN_PASSWORD="${PROD_ADMIN_PASSWORD:-}"
GATEWAY_PM2="$(pm2_live_ms_name mini-prod-ms-gateway || true)"
FAIL=0

pass() { echo "OK  $*"; }
fail() { echo "FAIL $*"; FAIL=1; }

echo "=== Gateway YAML commerce route parity (repo) ==="
if [ -x "${SCRIPT_DIR}/verify-gateway-commerce-route-parity.sh" ]; then
  if REPO_ROOT="$REPO_ROOT" bash "${SCRIPT_DIR}/verify-gateway-commerce-route-parity.sh"; then
    pass "commerce Path= predicates match across gateway profiles"
  else
    fail "gateway commerce route parity — fix YAML or run verify-gateway-commerce-route-parity.sh"
  fi
else
  pass "parity script not present (skipped)"
fi

echo ""
echo "=== Env (must not contain SPRING_CLOUD_GATEWAY route overrides) ==="
if compgen -e | grep -qE '^SPRING_CLOUD_GATEWAY.*ROUTES'; then
  fail "SPRING_CLOUD_GATEWAY*ROUTES* still in shell env — run prod-fix-gateway-route-env.sh"
else
  pass "no SPRING_CLOUD_GATEWAY route env in current shell"
fi
if [ -f /etc/mini-and-more/prod.env ] && grep -qE '^SPRING_CLOUD_GATEWAY.*ROUTES' /etc/mini-and-more/prod.env; then
  fail "prod.env still has SPRING_CLOUD_GATEWAY route lines"
else
  pass "prod.env clean"
fi

echo ""
echo "=== Service health ==="
for spec in "gateway:${GATEWAY_PORT}" "integration:${INTEGRATION_PORT}" "store:9181"; do
  name="${spec%%:*}"
  port="${spec##*:}"
  code=$(curl -sS -m 8 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${port}/management/health/liveness" || echo ERR)
  if [ "$code" = "200" ]; then pass "${name} :${port} liveness"; else fail "${name} :${port} → ${code}"; fi
done

echo ""
echo "=== Gateway routes (current process only) ==="
if [ -z "${GATEWAY_PM2:-}" ]; then
  fail "gateway PM2 process not found (mini-prod-ms-gateway)"
  GATEWAY_LOG_TAIL=""
else
  GATEWAY_LOG_TAIL=$(pm2 logs "$GATEWAY_PM2" --nostream --lines 500 2>/dev/null || true)
fi
# Only inspect logs since the last successful boot (ignore old crash-loop lines).
GATEWAY_LOG_SINCE_BOOT=$(printf '%s\n' "$GATEWAY_LOG_TAIL" | awk '
  /Started GatewayApp/ { buf = ""; capture = 1 }
  capture { buf = buf $0 ORS }
  END { printf "%s", buf }
')
if [ -z "$GATEWAY_LOG_SINCE_BOOT" ]; then
  fail "gateway not started — pm2 logs ${GATEWAY_PM2:-mini-prod-ms-gateway} --lines 40"
elif printf '%s\n' "$GATEWAY_LOG_SINCE_BOOT" | grep -qE 'routes\[0\]\.(uri|predicates)|APPLICATION FAILED TO START'; then
  fail "gateway failed after last boot — check pm2 logs ${GATEWAY_PM2:-mini-prod-ms-gateway}"
else
  pass "Started GatewayApp with no route binding errors since last boot"
fi

echo ""
echo "=== Nginx Zoho timeouts (live vhost only) ==="
NGINX_SITE="$(find_prod_nginx_site || true)"
if [ -z "$NGINX_SITE" ]; then
  fail "could not find live minisnmore.com nginx config"
elif [[ "$NGINX_SITE" == *".bak"* ]]; then
  fail "nginx resolver returned backup file: ${NGINX_SITE}"
elif grep -q 'location /api/v1/admin/zoho/' "$NGINX_SITE"; then
  pass "nginx live site: ${NGINX_SITE}"
  pass "nginx has /api/v1/admin/zoho/ block"
  if awk '
    /location \/api\/v1\/admin\/zoho\// { inblock=1 }
    inblock && /proxy_read_timeout 600s/ { found=1 }
    inblock && /^[[:space:]]*}/ { inblock=0 }
    END { exit found ? 0 : 1 }
  ' "$NGINX_SITE"; then
    pass "nginx Zoho proxy_read_timeout 600s"
  else
    fail "nginx Zoho block missing 600s on LIVE site — run prod-apply-nginx-zoho-timeouts.sh"
  fi
else
  fail "live nginx missing Zoho location — run prod-apply-nginx-zoho-timeouts.sh"
fi

echo ""
echo "=== API path version (microservice split) ==="
# Legacy JHipster /api/{entity} CRUD was removed; only /api/v1/** (+ gateway /api/authenticate) are valid.
legacy_code=$(curl -sS -m 10 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${GATEWAY_PORT}/api/products" || echo ERR)
v1_code=$(curl -sS -m 10 -o /dev/null -w "%{http_code}" "http://127.0.0.1:${GATEWAY_PORT}/api/v1/categories" || echo ERR)
if [ "$legacy_code" = "404" ] || [ "$legacy_code" = "401" ] || [ "$legacy_code" = "403" ]; then
  pass "legacy GET /api/products → ${legacy_code} (expected — use /api/v1/**)"
else
  fail "legacy GET /api/products → ${legacy_code} (expected 404; stale monolith route?)"
fi
if [ "$v1_code" = "200" ]; then
  pass "GET /api/v1/categories → 200"
else
  fail "GET /api/v1/categories → ${v1_code}"
fi

echo ""
echo "=== Zoho API via gateway (admin JWT) ==="
if [ -z "$ADMIN_PASSWORD" ]; then
  echo "SKIP admin JWT checks (set PROD_ADMIN_PASSWORD)"
else
  # shellcheck source=lib/encrypted-env.sh
  source "${SCRIPT_DIR}/lib/encrypted-env.sh"
  mini_load_host_env prod || true
  TOKEN=$(curl -sS -m 15 -X POST "http://127.0.0.1:${GATEWAY_PORT}/api/authenticate" \
    -H "Content-Type: application/json" \
    -d "{\"username\":\"${ADMIN_EMAIL}\",\"password\":\"${ADMIN_PASSWORD}\"}" \
    | python3 -c "import sys,json; print(json.load(sys.stdin).get('id_token',''))" 2>/dev/null || true)
  if [ -z "$TOKEN" ]; then
    fail "could not get admin JWT"
  else
    pass "admin JWT obtained"
    for path in \
      "GET:/api/v1/admin/zoho/integration-status" \
      "GET:/api/v1/admin/zoho/product-sync-status" \
      "GET:/api/v1/admin/zoho-settings" \
      "POST:/api/v1/admin/zoho/sync-products"; do
      method="${path%%:*}"
      url="${path#*:}"
      code=$(curl -sS -m 30 -o /tmp/zoho-verify-body.txt -w "%{http_code}" -X "$method" \
        -H "Authorization: Bearer ${TOKEN}" "http://127.0.0.1:${GATEWAY_PORT}${url}" || echo ERR)
      if [ "$code" = "200" ]; then
        pass "${method} ${url} → 200"
        if [ "$url" = "/api/v1/admin/zoho/sync-products" ] && ! grep -qE '"success"|"failed"' /tmp/zoho-verify-body.txt 2>/dev/null; then
          fail "sync-products 200 but missing sync summary — deploy catalog JAR"
        fi
      elif [ "$code" = "502" ] && [ "$url" = "/api/v1/admin/zoho/integration-status" ]; then
        fail "${url} → 502 (Zoho quota/API — not 504 timeout)"
      elif [ "$code" = "504" ] || [ "$code" = "ERR" ]; then
        fail "${method} ${url} → ${code} (timeout/routing — check gateway+nginx)"
      else
        fail "${method} ${url} → ${code}"
      fi
    done
  fi
fi

echo ""
if [ "$FAIL" -eq 0 ]; then
  echo "All checks passed — Zoho admin is ready."
  exit 0
fi
echo "Some checks failed."
echo "Note: if Zoho API lines above show 200, the stack works; fix nginx/gateway items above."
exit 1
