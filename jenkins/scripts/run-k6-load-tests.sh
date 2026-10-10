#!/usr/bin/env bash
# k6 load tests at 10, 25, and 50 virtual users (all resolvable GET endpoints).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$ROOT"

API_TEST_BASE_URL="${API_TEST_BASE_URL:-https://uat.minisnmore.com}"
K6_VUS_LEVELS="${K6_VUS_LEVELS:-10,25,50}"
K6_DURATION="${K6_DURATION:-45s}"
K6_MAX_RESPONSE_MS="${K6_MAX_RESPONSE_MS:-3000}"
K6_VERSION="${K6_VERSION:-0.54.0}"
K6_URLS_FILE="${K6_URLS_FILE:-artifacts/load/k6-urls.json}"
K6_URLS_FILE_ABS="${ROOT}/${K6_URLS_FILE}"
K6_SCRIPT="${K6_SCRIPT:-${ROOT}/deploy/load/k6-all-gets.js}"
K6_FAIL_ON_THRESHOLDS="${K6_FAIL_ON_THRESHOLDS:-false}"

export API_TEST_BASE_URL

mkdir -p artifacts/load

ensure_k6() {
  if command -v k6 >/dev/null 2>&1; then
    return
  fi
  echo "Installing k6 ${K6_VERSION} into workspace..."
  mkdir -p .jenkins-tools
  arch="$(uname -m)"
  case "$arch" in
    x86_64|amd64) k6_arch="amd64" ;;
    aarch64|arm64) k6_arch="arm64" ;;
    *) echo "Unsupported k6 architecture: $arch" >&2; exit 1 ;;
  esac
  tarball="k6-v${K6_VERSION}-linux-${k6_arch}.tar.gz"
  curl -fsSL "https://github.com/grafana/k6/releases/download/v${K6_VERSION}/${tarball}" -o ".jenkins-tools/${tarball}"
  rm -rf ".jenkins-tools/k6-v${K6_VERSION}-linux-${k6_arch}"
  tar -xzf ".jenkins-tools/${tarball}" -C .jenkins-tools
  export PATH="${ROOT}/.jenkins-tools/k6-v${K6_VERSION}-linux-${k6_arch}:${PATH}"
}

fetch_tokens() {
  python3 - <<'PY'
import json
import os
import ssl
import urllib.request

base = os.environ["API_TEST_BASE_URL"].rstrip("/")
ctx = ssl.create_default_context()

def login(user, password):
    req = urllib.request.Request(
        f"{base}/api/authenticate",
        data=json.dumps({"username": user, "password": password}).encode(),
        headers={"Content-Type": "application/json", "Accept": "application/json"},
        method="POST",
    )
    try:
        with urllib.request.urlopen(req, timeout=30, context=ctx) as resp:
            data = json.loads(resp.read())
        return data.get("id_token") or data.get("idToken") or ""
    except Exception:
        return ""

admin = login(os.environ.get("API_TEST_ADMIN_USER", "admin"), os.environ.get("API_TEST_ADMIN_PASS", "admin6"))
cust = login(
    os.environ.get("API_TEST_CUSTOMER_USER", "irfanpatan9948@gmail.com"),
    os.environ.get("API_TEST_CUSTOMER_PASS", "Zero@1234"),
)
out = os.environ["K6_TOKEN_FILE"]
with open(out, "w", encoding="utf-8") as f:
    f.write(f'K6_ADMIN_TOKEN={admin}\n')
    f.write(f'K6_CUSTOMER_TOKEN={cust}\n')
PY
}

echo "=== Prepare k6 endpoint list (all GET) ==="
python3 deploy/load/prepare-k6-urls.py --output "$K6_URLS_FILE"

K6_TOKEN_FILE="artifacts/load/k6-tokens.env"
export K6_TOKEN_FILE
fetch_tokens
# shellcheck disable=SC1090
source "$K6_TOKEN_FILE"

ensure_k6

IFS=',' read -ra VUS_ARRAY <<< "$K6_VUS_LEVELS"
SUMMARY_FILE="artifacts/load/k6-summary.json"
echo '{"runs":[]}' > "$SUMMARY_FILE"

for vus in "${VUS_ARRAY[@]}"; do
  vus="$(echo "$vus" | tr -d ' ')"
  [ -n "$vus" ] || continue
  echo ""
  echo "=== k6 load test: ${vus} virtual users, ${K6_DURATION} ==="
  report_json="artifacts/load/k6-${vus}vu.json"
  endpoint_report="artifacts/load/k6-endpoints-${vus}vu.json"
  set +e
  k6 run \
    --vus "$vus" \
    --duration "$K6_DURATION" \
    -e "BASE_URL=${API_TEST_BASE_URL}" \
    -e "K6_URLS_FILE=${K6_URLS_FILE_ABS}" \
    -e "K6_MAX_RESPONSE_MS=${K6_MAX_RESPONSE_MS}" \
    -e "K6_ADMIN_TOKEN=${K6_ADMIN_TOKEN:-}" \
    -e "K6_CUSTOMER_TOKEN=${K6_CUSTOMER_TOKEN:-}" \
    -e "K6_VUS=${vus}" \
    -e "K6_DURATION=${K6_DURATION}" \
    -e "K6_ENDPOINT_REPORT=${ROOT}/${endpoint_report}" \
    --summary-export "$report_json" \
    "$K6_SCRIPT"
  k6_status=$?
  set -e
  if [ "$k6_status" -ne 0 ]; then
    echo "WARN: k6 exited ${k6_status} for ${vus} VUs (thresholds or errors). See ${report_json}" >&2
    if [ "$K6_FAIL_ON_THRESHOLDS" = "true" ]; then
      exit "$k6_status"
    fi
  fi

  python3 - <<PY
import json
from pathlib import Path
summary_path = Path("${SUMMARY_FILE}")
run_path = Path("${report_json}")
data = json.loads(summary_path.read_text())
entry = {"vus": int("${vus}"), "duration": "${K6_DURATION}", "report": str(run_path), "endpointReport": str(Path("${endpoint_report}"))}
if run_path.is_file():
    entry["metrics"] = json.loads(run_path.read_text())
endpoint_path = Path("${endpoint_report}")
if endpoint_path.is_file():
    entry["endpoints"] = json.loads(endpoint_path.read_text())
data["runs"].append(entry)
summary_path.write_text(json.dumps(data, indent=2))
PY
done

echo "k6 endpoint metrics: artifacts/load/k6-endpoints-*vu.json (merged into artifacts/postman/index.html)"
