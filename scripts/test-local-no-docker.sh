#!/usr/bin/env bash
# Unit tests only (no Docker, no *IT* integration tests, no Testcontainers).
# Each module enforces JaCoCo LINE coverage >= 95% (Vitest thresholds 95% on scoped frontend files).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

echo "==> Frontend type and lint checks"
npm --prefix frontend run typecheck
npm --prefix frontend run lint

echo "==> Frontend (Vitest + 95% coverage on scoped lib/routes)"
npm --prefix frontend run test:coverage

echo "==> Gateway unit tests (Surefire; Failsafe *IT* skipped)"
(cd services/gateway && ./mvnw -q test)

echo "==> Catalog core unit tests (Surefire; Failsafe *IT* skipped)"
(cd backend && ./mvnw -q test)

echo "==> Store unit tests"
(cd services/store && ./mvnw -q test)

echo "==> Commerce unit tests"
(cd services/commerce && ./mvnw -q test)

echo ""
echo "All unit tests passed (95% coverage gate per module)."
echo ""
echo "Not run here (integration / E2E / load — require Docker or live stack):"
echo "  cd backend && ./mvnw verify -DskipITs=false   # *IT* only if you explicitly enable Failsafe"
echo "  bash scripts/verify-frontend-api-contract.sh"
echo "  bash scripts/dev-start-microservices.sh"
