#!/usr/bin/env bash
# Run after `jhipster` / `jhipster jdl` in backend/ to keep pure microservice layout.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
BACKEND="$ROOT/backend"

# JHipster sometimes regenerates com.minisandmore (legacy typo package).
rm -rf "$BACKEND/src/main/java/com/minisandmore" "$BACKEND/src/test/java/com/minisandmore"

find "$BACKEND/src/main/java/com/miniandmore/app/web/rest" -maxdepth 1 -name '*Resource.java' -delete 2>/dev/null || true
find "$BACKEND/src/main/java/com/miniandmore/app/web/rest" -maxdepth 1 -name 'AuthenticateController.java' -delete 2>/dev/null || true
find "$BACKEND/src/main/java/com/miniandmore/app/web/rest" -maxdepth 1 -name 'AccountResource.java' -delete 2>/dev/null || true
find "$BACKEND/src/main/java/com/miniandmore/app/web/rest" -maxdepth 1 -name 'UserResource.java' -delete 2>/dev/null || true
find "$BACKEND/src/main/java/com/miniandmore/app/web/rest" -maxdepth 1 -name 'PublicUserResource.java' -delete 2>/dev/null || true
find "$BACKEND/src/main/java/com/miniandmore/app/web/rest" -maxdepth 1 -name 'AuthorityResource.java' -delete 2>/dev/null || true

rm -f "$BACKEND/src/main/java/com/miniandmore/app/config/FeignConfiguration.java" 2>/dev/null || true
rm -rf "$BACKEND/src/main/java/com/miniandmore/app/client" 2>/dev/null || true

echo "Microservice cleanup done. Only com.miniandmore.app/web/rest/v1 should remain for HTTP APIs."
echo "Next: cd backend && ./mvnw compile -DskipTests"
