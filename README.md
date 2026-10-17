# Minis & More

Storefront and admin frontend with a catalog core and three Java services.

```text
frontend/             React/TanStack app, npm manifests, tooling, tests, and static assets
backend/              Core catalog, business logic, integrations, and runtime mail templates
services/
  gateway/            API gateway and authentication
  store/              Store API service
  commerce/           Commerce API service
  vault-bootstrap/    Shared env vault loader (install before other Java modules)
deploy/               Deployment scripts, Nginx configuration, environment examples, and API tooling
docker/               Shared local development infrastructure
jenkins/              CI, report generators, assets, and report templates
scripts/              Repository checks and developer utilities
docs/                 Architecture, runbooks, handover, design references, and historical material
                      See [docs/vault.md](docs/vault.md) for env vault setup and key rotation.
artifacts/            Generated CI reports and documentation
```

## Frontend

Run from the repository root:

```bash
npm --prefix frontend ci
npm --prefix frontend run dev
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix frontend run check:imports
```

Or run the usual npm commands from `frontend/`. The `@/` alias resolves to `frontend/src/`. Frontend build output is `frontend/dist/`; coverage is `frontend/coverage/`. npm and `frontend/package-lock.json` are used by deployment and CI.

## Core and services

Each Java application owns its Maven wrapper, configuration, source, tests, and resources:

```bash
(cd services/vault-bootstrap && ./mvnw install)
(cd backend && ./mvnw test)
(cd services/gateway && ./mvnw test)
(cd services/store && ./mvnw test)
(cd services/commerce && ./mvnw test)
```

Follow [local development](docs/LOCAL_DEV.md) for infrastructure and service startup. Frontend state belongs in `frontend/src/store/`; the Java store API lives in `services/store/`. Runtime emails live in `backend/src/main/resources/templates/mail/`; design references live in `docs/design/email/`.

## Deployment and verification

Use the [supported deployment scripts](deploy/scripts/README.md) and [production readiness guide](docs/PRODUCTION_READINESS.md). PM2 frontend commands use `frontend/` as their working directory. Java deployment commands use `backend/target/` and `services/*/target/`.

```bash
bash deploy/scripts/ensure-frontend-source.sh
npm --prefix frontend run check
bash scripts/verify-deployment-layout.sh
bash scripts/test-local-no-docker.sh
```

Keep secrets in environment files or the deployment secret store. Generated `node_modules/`, `dist/`, `coverage/`, service `target/`, backups, logs, and deployment JARs are ignored. Existing historical tracked reports remain for compatibility.

See the [verification report](docs/restructure-verification.md) for passing checks and the [production deployment record](docs/deployment-2026-10-16.md) for release details and rollback instructions.

The [structure and migration guide](docs/repository-structure.md) documents old-to-new paths. Historical snapshots in `docs/archive/` are reference material, outside the application build.
