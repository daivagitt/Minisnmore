#!/usr/bin/env bash
# Configure GitHub access for git pull/push on this VPS.
# Run as the deploy user (credentials land in that user's HOME / repo-local store).
#
# Usage (preferred — token never in shell history):
#   echo 'ghp_your_token_here' | sudo tee /etc/mini-and-more/github.token >/dev/null
#   sudo chmod 600 /etc/mini-and-more/github.token
#   bash deploy/scripts/setup-github-auth.sh
#
# Optional env:
#   REPO_ROOT          — defaults to repo root derived from this script
#   GITHUB_REMOTE_URL  — origin URL to set (default: mini_and_more GitHub repo)
#   GIT_FETCH_REF      — branch/ref to test-fetch (default: current branch)
set -euo pipefail

TOKEN_FILE="${GITHUB_TOKEN_FILE:-/etc/mini-and-more/github.token}"
REPO_ROOT="${REPO_ROOT:-$(cd "$(dirname "$0")/../.." && pwd)}"
GITHUB_REMOTE_URL="${GITHUB_REMOTE_URL:-https://github.com/developersqobox-org/mini_and_more.git}"

if [ -n "${GITHUB_TOKEN:-}" ]; then
  TOKEN="$GITHUB_TOKEN"
elif [ -f "$TOKEN_FILE" ]; then
  TOKEN="$(tr -d '[:space:]' < "$TOKEN_FILE")"
else
  echo "ERROR: No GitHub token found." >&2
  echo "  Create ${TOKEN_FILE} (chmod 600) or set GITHUB_TOKEN." >&2
  echo "  Token needs 'repo' scope for developersqobox-org/mini_and_more." >&2
  exit 1
fi

if [ -z "$TOKEN" ]; then
  echo "ERROR: Token file ${TOKEN_FILE} is empty." >&2
  exit 1
fi

if ! command -v gh >/dev/null 2>&1; then
  echo "ERROR: gh CLI required. Run: apt install gh" >&2
  exit 1
fi

if [ ! -d "$REPO_ROOT/.git" ]; then
  echo "ERROR: ${REPO_ROOT} is not a git repository." >&2
  exit 1
fi

CREDENTIALS_FILE="${GIT_CREDENTIALS_FILE:-${REPO_ROOT}/.git/github-credentials}"
echo "=== Configuring git HTTPS credentials (repo-local) ==="
printf 'https://x-access-token:%s@github.com\n' "$TOKEN" > "$CREDENTIALS_FILE"
chmod 600 "$CREDENTIALS_FILE"
git -C "$REPO_ROOT" config --local credential.helper "store --file ${CREDENTIALS_FILE}"
git -C "$REPO_ROOT" remote set-url origin "$GITHUB_REMOTE_URL"

if printf '%s\n' "$TOKEN" | gh auth login --with-token 2>/dev/null; then
  gh auth setup-git
  echo "gh CLI authenticated."
else
  echo "WARN: gh auth skipped (token may lack read:org). Git HTTPS fetch/pull still works."
fi

FETCH_REF="${GIT_FETCH_REF:-$(git -C "$REPO_ROOT" symbolic-ref -q --short HEAD || true)}"
if [ -n "$FETCH_REF" ]; then
  echo "=== Testing git fetch (origin ${FETCH_REF}) ==="
  git -C "$REPO_ROOT" fetch origin "$FETCH_REF" 2>&1
else
  echo "=== Skipping git fetch test (no GIT_FETCH_REF / detached HEAD) ==="
fi

echo ""
echo "OK — GitHub auth is working."
if gh auth status >/dev/null 2>&1; then
  gh auth status 2>&1 | sed 's/^/  /'
fi
