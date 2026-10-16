#!/usr/bin/env bash
# CodeRabbit review for gateway module in sub-chunks (150-file CLI limit).
# Use when "gateway/" (230+ files) or services/gateway/src/main/java is too large.
#
# Usage:
#   git add -A
#   bash scripts/coderabbit-review-gateway-split.sh
#
# Optional: SKIP_DONE=1 skips chunks already reviewed via CLI (see COMPLETED_CHUNKS).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

LOG="${ROOT}/artifacts/coderabbit-review-gateway-split-$(date +%Y%m%d-%H%M%S).md"
mkdir -p "${ROOT}/artifacts"

# Labels completed in prior sessions (store in COMPLETED_CHUNKS to skip on re-run).
COMPLETED_CHUNKS="${COMPLETED_CHUNKS:-Gateway Java main|Gateway Java test|Gateway resources|Gateway docker}"

echo "# CodeRabbit gateway split — $(date -u +%Y-%m-%dT%H:%MZ)" | tee "$LOG"
echo "Branch: $(git branch --show-current)" | tee -a "$LOG"
echo "SKIP_DONE=${SKIP_DONE:-0} COMPLETED_CHUNKS=${COMPLETED_CHUNKS}" | tee -a "$LOG"
echo "" | tee -a "$LOG"

is_completed() {
  local label="$1"
  [[ "${SKIP_DONE:-0}" != "1" ]] && return 1
  [[ ",${COMPLETED_CHUNKS}," == *",${label},"* ]]
}

run_chunk() {
  local label="$1"
  local dir="$2"
  if is_completed "$label"; then
    echo "=== $label — skip (marked done) ===" | tee -a "$LOG"
    return 0
  fi
  local count
  count=$(git diff --cached --name-only -- "$dir" 2>/dev/null | wc -l | tr -d ' ')
  if [ "$count" -eq 0 ]; then
    count=$(git diff --name-only HEAD -- "$dir" 2>/dev/null | wc -l | tr -d ' ')
  fi
  if [ "$count" -eq 0 ]; then
    echo "=== $label — skip (0 files: $dir) ===" | tee -a "$LOG"
    return 0
  fi
  echo "" | tee -a "$LOG"
  echo "=== $label ($count files: $dir) ===" | tee -a "$LOG"
  if [ "$count" -gt 150 ]; then
    echo "ERROR: $count > 150 — split $dir further." | tee -a "$LOG"
    return 1
  fi
  if coderabbit review --plain --type uncommitted --dir "$dir" 2>&1 | tee -a "$LOG"; then
    return 0
  fi
  echo "[FAILED: $label]" | tee -a "$LOG"
  return 1
}

# Java: one package per chunk (largest is web @ ~20 files; whole tree was ~84).
CHUNKS=(
  "Gateway config Java|services/gateway/src/main/java/com/miniandmore/gateway/config"
  "Gateway security|services/gateway/src/main/java/com/miniandmore/gateway/security"
  "Gateway repository|services/gateway/src/main/java/com/miniandmore/gateway/repository"
  "Gateway service|services/gateway/src/main/java/com/miniandmore/gateway/service"
  "Gateway web|services/gateway/src/main/java/com/miniandmore/gateway/web"
  "Gateway domain|services/gateway/src/main/java/com/miniandmore/gateway/domain"
  "Gateway management|services/gateway/src/main/java/com/miniandmore/gateway/management"
  "Gateway aop|services/gateway/src/main/java/com/miniandmore/gateway/aop"
  "Gateway resources YAML|services/gateway/src/main/resources/config"
  "Gateway resources templates|services/gateway/src/main/resources/templates"
  "Gateway resources i18n|services/gateway/src/main/resources/i18n"
  "Gateway resources|services/gateway/src/main/resources"
  "Gateway test|services/gateway/src/test/java"
  "Gateway docker|services/gateway/src/main/docker"
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
echo "Note: GatewayApp.java / package-info (3 files) were in the prior 'Gateway Java main' (~84 file) review." | tee -a "$LOG"
echo "      'Gateway resources' overlaps YAML/templates/i18n — set SKIP_DONE=1 if that full chunk was already run." | tee -a "$LOG"
echo "" | tee -a "$LOG"
echo "Report: $LOG" | tee -a "$LOG"
exit "$FAILED"
