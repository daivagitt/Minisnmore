#!/usr/bin/env bash
# Storefront source of truth is src/ (TanStack/Vite build). This script prevents
# regressions from editing or deploying stale root-level copies.
set -euo pipefail

REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
cd "$REPO_ROOT"

link_if_missing() {
  local name="$1"
  local target="src/$1"
  if [ ! -d "$target" ]; then
    echo "ERROR: missing $target" >&2
    exit 1
  fi
  if [ -L "$name" ]; then
    return 0
  fi
  if [ -d "$name" ]; then
    echo "WARN: $name/ is a real directory; expected symlink to $target/" >&2
    echo "      Run: mv $name ${name}._backup && ln -s $target $name" >&2
    exit 1
  fi
  ln -s "$target" "$name"
  echo "Linked $name -> $target"
}

link_if_missing routes
link_if_missing components

# Never copy root -> src (that reverts fixes). Sync src -> root mirrors only.
for dir in hooks lib data; do
  if [ -d "src/$dir" ] && [ -d "$dir" ] && [ ! -L "$dir" ]; then
    rsync -a "src/$dir/" "$dir/"
  fi
done

echo "OK: frontend source is src/ (routes + components symlinked)"
