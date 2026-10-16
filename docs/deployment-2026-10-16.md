# Production deployment — 16 October 2026

**Status:** deployed successfully to https://minisnmore.com. Release: `layout-20261016T091234Z`. Cutover ran from 09:20:04 to 09:21:25 UTC; final stability verification was recorded at 09:22:45 UTC.

## Steps completed

1. **Reverify the reorganized source.** Frontend TypeScript and lint passed with zero errors (23 existing lint warnings). All 1,114 frontend tests and all four Java test suites passed. The import audit verified 2,215 imports/exports across 514 files. Coverage thresholds, dependency checks, and deployment-layout checks passed. See the [verification report](restructure-verification.md) for coverage and Java test totals.
2. **Check production and capture rollback material.** Verified baseline application health, ports, available capacity, and Nginx configuration. Retained the previous four JARs, frontend build, PM2 configuration, environment file, Nginx configuration, and a source manifest. Backup directories and files are restricted because configuration backups contain secrets.
3. **Build the release separately.** Packaged core from `backend/` and gateway/store/commerce from `services/` using isolated source copies. Built the frontend from `frontend/` with the production origin and existing environment. Verified JAR integrity and source hashes. The release manifest records artifact checksums and that the release includes uncommitted workspace changes.
4. **Verify schema and preview.** Compared all 137 Liquibase files against the previous production core artifact: they were byte-identical. No database migration was required or executed. Previewed the new frontend on a separate local port; seven pages and their bundled assets passed. Retained 129 prior hashed assets so cached browser pages can continue loading their assets.
5. **Deploy services sequentially.** Replaced core, store, commerce, gateway, then frontend. Each service had to pass repeated health/readiness checks before the next replacement. Existing runtime credentials and port settings were preserved. Java processes now use immutable release JARs and the canonical module working directories. `frontend/dist` points to the release frontend output. This single-instance rollout involved brief service restarts; it was not a zero-downtime deployment.
6. **Validate and reload Nginx.** Ran `nginx -t` successfully and reloaded Nginx with the updated frontend static paths. Verified the public release marker matches the new release.
7. **Verify public behavior.** Catalog, product, and site-settings APIs returned HTTP 200. Account, order, and admin APIs returned HTTP 401 for unauthenticated requests. `/`, `/login`, `/shop`, `/checkout`, `/admin/login`, `/account`, and `/track` returned HTML 200, and all referenced JavaScript/CSS assets loaded with valid content types. Favicon and robots checks passed.
8. **Save and observe.** Saved the PM2 process list. Three subsequent checks found all five application processes online, all Java liveness/readiness endpoints healthy, and zero unexpected restarts. The temporary preview was stopped.

## Release locations

All paths below are relative to `/var/www/uat1/mini_ms_test`:

- Release artifacts and checksums: `deploy/releases/layout-20261016T091234Z/`
- Deployment event record: `deploy/releases/layout-20261016T091234Z/deployment-record.json`
- Public checks and process stability record: `deploy/releases/layout-20261016T091234Z/post-deploy-verification.json`
- Protected backups and rollback tooling: `deploy/backups/layout-20261016T091234Z/`

The deployed layout is `frontend/`, `backend/`, and `services/{gateway,store,commerce}/`. Production credentials were preserved and are not included in this report.

## Rollback if needed

The prepared rollback script is retained and syntax-checked; it was not executed because deployment succeeded. Run it on this production host with the same privileges used for PM2 and Nginx:

```bash
python3 /var/www/uat1/mini_ms_test/deploy/backups/layout-20261016T091234Z/rollback-production.py
```

It checks that this release is active, verifies the old JAR backups, restores the previous frontend and service artifacts, waits for health checks, validates/reloads Nginx, and saves PM2 state. It restarts the services and therefore causes brief interruptions. No database rollback is needed for this release because no schema changes were applied. Retain these backups while this release is in use.

## Verification limits

Public checks cover HTTP/SSR delivery, bundled assets, service health, and unauthenticated access guards. No real customer orders, payments, refunds, or emails were triggered. Authenticated end-to-end checkout and email delivery were not exercised against production, and a separate staging environment was not used.
