#!/usr/bin/env bash
# Local dev: infra + catalog core + store + commerce + integration + gateway.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

DOCKER="${DOCKER:-docker}"
if command -v "$DOCKER" >/dev/null 2>&1; then
  $DOCKER compose -f docker/microservices.yml up -d
  echo "Waiting for Postgres/Consul..."
  sleep 8
else
  echo "WARN: docker not in PATH — start Consul, Postgres (5432/5433), Redis manually." >&2
fi

echo "Starting catalog core (8084)..."
(cd backend && ./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev) &
CORE_PID=$!

echo "Waiting for catalog core..."
for i in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8084/management/health >/dev/null 2>&1; then
    echo "Catalog core ready (${i} attempts)."
    break
  fi
  sleep 2
done

echo "Starting store (8081)..."
(cd store && ./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev) &
STORE_PID=$!

echo "Waiting for store..."
for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8081/management/health >/dev/null 2>&1; then
    echo "Store ready (${i} attempts)."
    break
  fi
  sleep 2
done

echo "Starting commerce (8082)..."
(cd commerce && ./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev) &
COMMERCE_PID=$!

for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8082/management/health >/dev/null 2>&1; then
    echo "Commerce ready (${i} attempts)."
    break
  fi
  sleep 2
done

echo "Starting integration (8083)..."
(cd integration && ./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev) &
INTEGRATION_PID=$!

for i in $(seq 1 60); do
  if curl -fsS http://127.0.0.1:8083/management/health >/dev/null 2>&1; then
    echo "Integration ready (${i} attempts)."
    break
  fi
  sleep 2
done

echo "Starting gateway (8080)..."
(cd gateway && ./mvnw -q -DskipTests spring-boot:run -Dspring-boot.run.profiles=dev) &
GATEWAY_PID=$!

for i in $(seq 1 90); do
  if curl -fsS http://127.0.0.1:8080/management/health >/dev/null 2>&1; then
    echo "Gateway ready (${i} attempts)."
    break
  fi
  sleep 2
done

echo "Core=$CORE_PID Store=$STORE_PID Commerce=$COMMERCE_PID Integration=$INTEGRATION_PID Gateway=$GATEWAY_PID"
echo "Frontend: npm run dev  (proxies /api → :8080)"
if bash deploy/scripts/health-check-microservices.sh 2>/dev/null; then
  bash scripts/verify-frontend-api-contract.sh 2>/dev/null || true
fi
trap 'kill $CORE_PID $STORE_PID $COMMERCE_PID $INTEGRATION_PID $GATEWAY_PID 2>/dev/null || true' EXIT
wait
