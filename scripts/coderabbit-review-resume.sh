#!/usr/bin/env bash
# Resume CodeRabbit reviews (chunks not yet completed). Requires: git add -A
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

LOG="${ROOT}/artifacts/coderabbit-review-resume-$(date +%Y%m%d-%H%M%S).md"
mkdir -p "${ROOT}/artifacts"

echo "# CodeRabbit resume — $(date -u +%Y-%m-%dT%H:%MZ)" | tee "$LOG"
echo "Branch: $(git branch --show-current)" | tee -a "$LOG"
echo "" | tee -a "$LOG"

run_chunk() {
  local label="$1" dir="$2"
  local count
  count=$(git diff --cached --name-only -- "$dir" 2>/dev/null | wc -l | tr -d ' ')
  [ "$count" -eq 0 ] && { echo "=== $label — skip (0) ===" | tee -a "$LOG"; return 0; }
  echo "" | tee -a "$LOG"
  echo "=== $label ($count files: $dir) ===" | tee -a "$LOG"
  if [ "$count" -gt 150 ]; then
    echo "SKIP: $count > 150 — split required" | tee -a "$LOG"
    return 1
  fi
  if coderabbit review --plain --type uncommitted --dir "$dir" 2>&1 | tee -a "$LOG"; then
    return 0
  fi
  echo "[FAILED: $label]" | tee -a "$LOG"
  return 1
}

CHUNKS=(
  "Backend service|backend/src/main/java/com/miniandmore/app/service"
  "Backend web|backend/src/main/java/com/miniandmore/app/web"
  "Backend domain|backend/src/main/java/com/miniandmore/app/domain"
  "Backend config|backend/src/main/java/com/miniandmore/app/config"
  "Backend repository+security|backend/src/main/java/com/miniandmore/app/repository"
  "Backend app root|backend/src/main/java/com/miniandmore/app/MiniandmoreApp.java"
  "Backend test service|backend/src/test/java/com/miniandmore/app/service"
  "Backend test domain|backend/src/test/java/com/miniandmore/app/domain"
  "Backend test web|backend/src/test/java/com/miniandmore/app/web"
  "Backend test config|backend/src/test/java/com/miniandmore/app/config"
  "Backend resources|backend/src/main/resources"
  "Backend docker|backend/src/main/docker"
  "Backend JDL|backend/jdl"
  "Legacy service removals|backend/src/main/java/com/minisandmore/app/service"
  "Legacy web removals|backend/src/main/java/com/minisandmore/app/web"
  "Legacy domain removals|backend/src/main/java/com/minisandmore/app/domain"
  "Legacy config removals|backend/src/main/java/com/minisandmore/app/config"
  "Legacy test service|backend/src/test/java/com/minisandmore/app/service"
  "Legacy test web|backend/src/test/java/com/minisandmore/app/web"
  "Frontend|src"
  "Deploy|deploy"
  "Docs|docs"
)

FAILED=0
for entry in "${CHUNKS[@]}"; do
  label="${entry%%|*}"
  dir="${entry#*|}"
  if ! run_chunk "$label" "$dir"; then
    FAILED=$((FAILED + 1))
    echo "Stopped at: $label" | tee -a "$LOG"
    break
  fi
  sleep 2
done

echo "" | tee -a "$LOG"
echo "Report: $LOG" | tee -a "$LOG"
exit "$FAILED"
