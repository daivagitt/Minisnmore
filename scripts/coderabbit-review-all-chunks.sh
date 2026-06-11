#!/usr/bin/env bash
# CodeRabbit CLI review in chunks (150-file limit per run).
# Usage: git add -A   # optional, to include untracked in review
#        bash scripts/coderabbit-review-all-chunks.sh
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

LOG="${ROOT}/artifacts/coderabbit-review-$(date +%Y%m%d-%H%M%S).md"
mkdir -p "${ROOT}/artifacts"

echo "# CodeRabbit chunked review — $(date -u +%Y-%m-%dT%H:%MZ)" | tee "$LOG"
echo "Branch: $(git branch --show-current)" | tee -a "$LOG"
echo "Changed files (index + worktree): $(git diff --name-only HEAD 2>/dev/null | wc -l | tr -d ' ')" | tee -a "$LOG"
echo "" | tee -a "$LOG"

run_chunk() {
  local label="$1"
  local dir="$2"
  local count
  count=$(git diff --cached --name-only -- "$dir" 2>/dev/null | wc -l | tr -d ' ')
  if [ "$count" -eq 0 ]; then
    count=$(git diff --name-only HEAD -- "$dir" 2>/dev/null | wc -l | tr -d ' ')
  fi
  if [ "$count" -eq 0 ]; then
    echo "=== $label — skip (0 files) ===" | tee -a "$LOG"
    return 0
  fi
  echo "" | tee -a "$LOG"
  echo "=== $label ($count files: $dir) ===" | tee -a "$LOG"
  if [ "$count" -gt 150 ]; then
    echo "ERROR: $count files exceeds 150 limit — split this directory further." | tee -a "$LOG"
    return 1
  fi
  if coderabbit review --plain --type uncommitted --dir "$dir" 2>&1 | tee -a "$LOG"; then
    return 0
  fi
  echo "[chunk failed: $label]" | tee -a "$LOG"
  return 1
}

CHUNKS=(
  "Store edge|store"
  "Commerce edge|commerce"
  "Integration|integration"
  "Gateway config Java|gateway/src/main/java/com/miniandmore/gateway/config"
  "Gateway security|gateway/src/main/java/com/miniandmore/gateway/security"
  "Gateway repository|gateway/src/main/java/com/miniandmore/gateway/repository"
  "Gateway service|gateway/src/main/java/com/miniandmore/gateway/service"
  "Gateway web|gateway/src/main/java/com/miniandmore/gateway/web"
  "Gateway domain|gateway/src/main/java/com/miniandmore/gateway/domain"
  "Gateway management|gateway/src/main/java/com/miniandmore/gateway/management"
  "Gateway aop|gateway/src/main/java/com/miniandmore/gateway/aop"
  "Gateway resources YAML|gateway/src/main/resources/config"
  "Gateway resources templates|gateway/src/main/resources/templates"
  "Gateway resources i18n|gateway/src/main/resources/i18n"
  "Gateway resources|gateway/src/main/resources"
  "Gateway test|gateway/src/test/java"
  "Gateway docker|gateway/src/main/docker"
  "Backend service layer|backend/src/main/java/com/miniandmore/app/service"
  "Backend main Java (excl service)|backend/src/main/java/com/miniandmore/app"
  "Backend service tests|backend/src/test/java/com/miniandmore/app/service"
  "Backend tests (excl service)|backend/src/test/java/com/miniandmore/app"
  "Backend resources|backend/src/main/resources"
  "Backend docker & jdl|backend/src/main/docker"
  "Backend JDL & jhipster|backend/jdl"
  "Backend legacy removals|backend/src/main/java/com/minisandmore"
  "Backend legacy test removals|backend/src/test/java/com/minisandmore"
  "Backend pom & config|backend/pom.xml"
  "Frontend|src"
  "Deploy|deploy"
  "Docs|docs"
  "Jenkins & root|Jenkinsfile"
)

FAILED=0
for entry in "${CHUNKS[@]}"; do
  label="${entry%%|*}"
  dir="${entry#*|}"
  if ! run_chunk "$label" "$dir"; then
    FAILED=$((FAILED + 1))
    echo "Stopping: rate limit or error (re-run later to continue)." | tee -a "$LOG"
    break
  fi
  sleep 2
done

echo "" | tee -a "$LOG"
echo "Report: $LOG" | tee -a "$LOG"
exit "$FAILED"
