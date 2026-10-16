# Repository structure and migration

Application code is divided into three top-level locations:

- `frontend/`: the entire React/TanStack application, npm manifests and lockfiles, Vite/Vitest/TypeScript/ESLint/Sonar configuration, static files, tests, dependencies, build output, and coverage.
- `backend/`: the complete Java core, including catalog business logic, integrations, persistence, migrations, and runtime email templates.
- `services/`: independent gateway, store, and commerce Java projects, including their Maven wrappers, tests, resources, and build configuration.

The frontend's internal relative paths and `@/` alias remain package-local. Java package names and imports remain unchanged because each service moved as a complete Maven project.

## Application paths

| Previous path | Current path |
| --- | --- |
| `src/`, `public/` | `frontend/src/`, `frontend/public/` |
| Root npm manifests, lockfiles, frontend tool configuration | `frontend/` |
| Root `node_modules/`, `dist/`, `.tanstack/` | `frontend/node_modules/`, `frontend/dist/`, `frontend/.tanstack/` |
| `coverage/frontend/` | `frontend/coverage/` |
| `backend/` | `backend/` |
| `gateway/` | `services/gateway/` |
| Java `store/` | `services/store/` |
| `commerce/` | `services/commerce/` |

From the repository root, use `npm --prefix frontend ...` for frontend commands and `(cd services/<name> && ./mvnw ...)` for services. `scripts/test-local-no-docker.sh` covers frontend coverage and all four Java test suites.

Deployment and CI use these locations directly. PM2's frontend working directory is `frontend/`. Frontend upload scripts transfer `frontend/dist/`, and JAR paths use `services/*/target/` or `backend/target/`. The gateway target symlink uses `../../../deploy/gateway-prod.jar`. Report publication URLs remain stable; only source report locations changed. A separate Integration application is no longer required by the startup scripts; integrations live in core.

## Reference material

| Previous path | Current path |
| --- | --- |
| `Email_templates/` | `docs/design/email/order-events/` |
| `Templates_set_2/` | `docs/design/email/post-order-workflows/` |
| `ChangeLogs_M1_M2_Completed.md`, `ChangeLogs_M2.md` | `docs/changelog/` |
| `PROD_SETUP.md` | `docs/archive/PROD_SETUP.md` (historical instructions) |
| `openapi-all.zip` | `docs/reference/zoho-books-openapi.zip` |
| `build/CATALOGUE.pdf` | `docs/reference/CATALOGUE.pdf` |
| `Untitled` | `docs/reference/sku-import-note.txt` |
| `report.html` | `jenkins/templates/engineering-qa.html` |
| Stale root frontend copies and `services/store/shop*.tsx` | `docs/archive/legacy-frontend/` |

Historical frontend copies preserve earlier edits and may contain unresolved historical imports. They are outside the active frontend package and must not be imported into application code. Root frontend aliases have been removed.

## Verification

```bash
bash deploy/scripts/ensure-frontend-source.sh
npm --prefix frontend run check:imports
npm --prefix frontend test
npm --prefix frontend run build
(cd backend && ./mvnw test)
(cd services/gateway && ./mvnw test)
(cd services/store && ./mvnw test)
(cd services/commerce && ./mvnw test)
```

The import audit uses the frontend TypeScript configuration to resolve static imports, re-exports, literal dynamic imports, and Vite asset references across source and config files. Production client/server builds and Java compilation provide additional resolution checks.

Generated output, secrets, deployment JARs, logs, backups, Python caches, and AppleDouble metadata are ignored. Existing tracked reports and downloaded tools were retained for compatibility.
