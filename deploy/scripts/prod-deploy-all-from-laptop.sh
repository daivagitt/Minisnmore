#!/usr/bin/env bash
# Build + deploy gateway, integration, frontend, and verify Zoho stack on UAT.
#
#   bash deploy/scripts/prod-deploy-all-from-laptop.sh
#
# Requires SSH to root@93.127.199.55 (password or key).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

echo "=== 1/2 Gateway + integration ==="
bash "$ROOT/deploy/scripts/prod-deploy-gateway-from-laptop.sh"

echo ""
echo "=== 2/2 Frontend (admin Zoho UI, product page fixes) ==="
bash "$ROOT/deploy/scripts/prod-deploy-frontend-from-laptop.sh"

echo ""
echo "=== Done ==="
echo "  Admin Zoho:  https://minisnmore.com/admin/zoho"
echo "  Release:     https://minisnmore.com/RELEASE.txt"
echo "  Hard-refresh browser (Cmd+Shift+R) after deploy."
