#!/usr/bin/env bash
# Validate source locations without modifying the checkout.
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$REPO_ROOT"

for path in routes components hooks lib data assets store test; do
  if [ ! -d "frontend/src/$path" ]; then
    echo "ERROR: missing canonical frontend directory frontend/src/$path" >&2
    exit 1
  fi
done
for path in src/router.tsx src/styles.css package.json package-lock.json tsconfig.json vite.config.ts vitest.config.ts; do
  if [ ! -f "frontend/$path" ]; then
    echo "ERROR: missing frontend/$path" >&2
    exit 1
  fi
done
for path in backend services/gateway services/store services/commerce; do
  if [ ! -f "$path/pom.xml" ]; then
    echo "ERROR: missing Java module $path/pom.xml" >&2
    exit 1
  fi
done
for path in src public package.json package-lock.json vite.config.ts tsconfig.json gateway store commerce routes components hooks lib data assets test router.tsx routeTree.gen.ts styles.css; do
  if [ -e "$path" ] || [ -L "$path" ]; then
    echo "ERROR: obsolete root path $path; use frontend/, backend/, or services/" >&2
    exit 1
  fi
done

echo "OK: frontend/, backend/, and services/ contain the application sources"
