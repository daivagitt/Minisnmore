#!/usr/bin/env bash
# Fetch live OpenAPI from catalog-core and refresh committed snapshots.
#
#   REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/export-openapi.sh
#
# Env:
#   CATALOG_PORT=9184
#   GATEWAY_PORT=9180
#   OUTPUT=deploy/postman/openapi.snapshot.json
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
CATALOG_PORT="${CATALOG_PORT:-9184}"
GATEWAY_PORT="${GATEWAY_PORT:-9180}"
OUTPUT="${OUTPUT:-$REPO_ROOT/deploy/postman/openapi.snapshot.json}"
TMP="$(mktemp)"

cleanup() {
  rm -f "$TMP"
}
trap cleanup EXIT

for url in \
  "http://127.0.0.1:${CATALOG_PORT}/v3/api-docs/catalog" \
  "http://127.0.0.1:${CATALOG_PORT}/v3/api-docs" \
  "http://127.0.0.1:${GATEWAY_PORT}/v3/api-docs" \
  "https://minisnmore.com/v3/api-docs/catalog" \
  "https://minisnmore.com/v3/api-docs"; do
  if curl -fsSL "$url" -o "$TMP" && python3 -c "import json; json.load(open('$TMP'))" 2>/dev/null; then
    mkdir -p "$(dirname "$OUTPUT")"
    cp "$TMP" "$OUTPUT"
    paths="$(python3 -c "import json; print(len(json.load(open('$OUTPUT')).get('paths',{})))")"
    echo "Exported OpenAPI from $url → $OUTPUT ($paths paths)"
    exit 0
  fi
done

echo "ERROR: could not fetch a valid OpenAPI document from catalog (:${CATALOG_PORT}), gateway (:${GATEWAY_PORT}), or production." >&2
echo "Ensure catalog-core runs with --spring.profiles.active=prod,api-docs" >&2
exit 1
