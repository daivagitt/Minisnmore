# Folder migration and readiness verification

The application now lives in `frontend/`, `backend/`, and `services/{gateway,store,commerce}/`. The local code and verification blockers identified after migration have been resolved. Production was deployed on 16 October 2026 as `layout-20261016T091234Z`; see the [deployment record and rollback instructions](deployment-2026-10-16.md).

## Final results

| Check | Result |
| --- | --- |
| Frontend TypeScript | Passed with strict settings; zero errors |
| Frontend ESLint | Passed; zero errors, 23 existing nonblocking warnings |
| Frontend import/export audit | 2,215 imports across 514 source/config files; zero unresolved imports or named exports |
| Frontend tests | 157 files, 1,114 tests passed |
| Coverage thresholds | Passed without lowering thresholds or excluding more files |
| Core Maven tests | Passed; Surefire XML totals 4,074 tests, zero failures/errors |
| Gateway Maven tests | Passed; 386 tests, zero failures/errors |
| Store Maven tests | Passed; 32 tests, zero failures/errors |
| Commerce Maven tests | Passed; 62 tests, zero failures/errors |
| Production frontend build | Client and SSR server built successfully in an isolated output directory |
| Running production-build preview | Seven pages returned HTML 200; all referenced bundled assets served successfully |
| UI source scenario checks | 1,135 passed across 91 screens; static code checks, not browser E2E |
| Deployment helpers | Stubbed deployment checks passed; the production release was subsequently deployed and verified |
| Gateway route parity | Passed for seven profiles plus catalog-split superset |
| Nginx configuration | Isolated checks and live `nginx -t` passed; production Nginx reloaded successfully |
| Script syntax | 68 shell scripts and 47 Python files validated |
| npm manifest/lockfile | Dependency declarations match |
| Report tooling | Coverage, unit-test/scenario, and QA dashboard packaging verified with temporary output |
| Patch whitespace | `git diff --check` passed |

Coverage remains scoped to the original business-critical files in `frontend/vitest.config.ts`:

| Metric | Actual | Required |
| --- | --- | --- |
| Statements | 96.45% | 95% |
| Branches | 92.56% | 92.5% |
| Functions | 98.32% | 95% |
| Lines | 97.96% | 95% |

## Repairs completed

- Corrected production and test types, API response contracts, schema input types, nullable values, browser API extensions, and test fixtures. Strict TypeScript checks remain enabled; source files and tests remain included.
- Corrected the optional Supabase client proxy type and narrowed server-auth responses before reading user/claim fields.
- Declared the missing Lovable auth dependency and repaired the shared Review type imports.
- Preserved inbound negative-stock rejection in core. Updated the outdated zero-clamping test and added a regression proving rejected stock does not mutate existing variants.
- Restored product bulk-import handlers with validation, SKU upserts, and preservation of existing variant metadata, galleries, and optional content when cells are blank.
- Corrected saved site-settings DTO mapping back into admin form field names.
- Corrected operations dashboard fields to the actual order API contract.
- Made payment-search parsing accept typed-link `pay=true` as well as legacy `pay=1`, with regression tests for both.
- Added post-order cancellation, manual refund, pickup/outbound booking, photo upload, inspection, and coupon boundary regressions. All use mocked APIs; no real refunds, orders, or mail were triggered.
- Updated PM2 frontend working directories, frontend/JAR paths, Nginx static-file root, source upload, dependency installation, local commands, Sonar settings, and report inputs.
- Frontend laptop deployment now uploads source and build configuration alongside built output, installs the locked dependencies before stopping the running frontend, and starts preview from `frontend/`.
- Removed mandatory health/startup checks for the deleted standalone Integration process. Integration logic runs inside core; legacy Integration health checks remain opt-in where supported. The Zoho diagnostic no longer retries a real order as part of a health check.
- Updated the current production runbook to remove deleted launchers and obsolete module paths.

## Isolation and limits

Before cutover, the new release was previewed on `127.0.0.1:13087`, separately from the running production frontend. After deployment, public checks covered `/`, `/login`, `/shop`, `/checkout`, `/admin/login`, `/account`, and `/track`, including their referenced JavaScript and CSS assets. Public catalog, product, and site-settings APIs returned successful responses; protected account, order, and admin endpoints rejected unauthenticated access.

Java tests required execution outside the sandbox for Mockito's Java-agent attachment. Production deployment then restarted the five application processes sequentially, verified health and readiness, validated and reloaded Nginx, and saved PM2 state. Three subsequent stability checks found all five processes online with zero restarts. Backups and a release-specific rollback script were retained.

These checks verify compilation, automated tests, HTTP delivery, asset resolution, authentication guards, and service health. They do not demonstrate a successful authenticated checkout, live payment/refund, or email delivery; no real customer transactions or mail were triggered.

Existing Vite chunk-size/mixed-import warnings and ESLint warnings remain nonblocking. No code, typecheck, test, import-resolution, or coverage failure remains in the checks above.

## Production deployment

The user authorized production deployment after fresh verification. Cutover completed at **09:21:25 UTC on 16 October 2026** at [minisnmore.com](https://minisnmore.com). All 137 Liquibase migration files were identical to the previous production artifact; no database migration was required or executed. See the [deployment record](deployment-2026-10-16.md) for the exact sequence, retained backups, and rollback command.

A separate staging environment was not used for this deployment. Real authenticated payment/email workflow validation remains outside the evidence above.

## Reproduce local checks

From the repository root:

```bash
npm --prefix frontend run check
bash scripts/verify-deployment-layout.sh
npm --prefix frontend run build
(cd backend && ./mvnw test)
(cd services/gateway && ./mvnw test)
(cd services/store && ./mvnw test)
(cd services/commerce && ./mvnw test)
```

`frontend`'s `check` script runs lint, TypeScript, the import/export audit, and the full frontend coverage suite. `scripts/test-local-no-docker.sh` combines frontend type/lint/coverage checks with all four Maven test suites.
