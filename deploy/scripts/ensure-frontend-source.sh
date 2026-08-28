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
    if [ "$(readlink "$name")" != "$target" ]; then
      echo "ERROR: $name points to $(readlink "$name"), expected $target" >&2
      exit 1
    fi
    return 0
  fi
  if [ -d "$name" ]; then
    if ! diff -qr "$name" "$target" >/dev/null; then
      echo "ERROR: $name/ differs from $target/; refusing automatic source migration" >&2
      exit 1
    fi
    local backup_dir="deploy/backups/frontend-source-$(date +%Y%m%d%H%M%S)"
    mkdir -p "$backup_dir"
    mv "$name" "$backup_dir/$name"
    echo "Moved identical legacy $name/ mirror to $backup_dir/$name"
  fi
  ln -s "$target" "$name"
  echo "Linked $name -> $target"
}

link_if_missing routes
link_if_missing components
link_if_missing hooks

# Never copy root -> src (that reverts fixes). Sync src -> root mirrors only.
for dir in lib data; do
  if [ -d "src/$dir" ] && [ -d "$dir" ] && [ ! -L "$dir" ]; then
    rsync -a "src/$dir/" "$dir/"
  fi
done

echo "OK: frontend source is src/ (routes + components + hooks symlinked)"
