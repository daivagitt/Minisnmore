# Production script index

Run server commands from `/var/www/uat1/mini_ms_test`, using the same OS account
that owns the production PM2 processes. This directory is production despite
the historical `uat1` path. Configuration is `/etc/mini-and-more/prod.env`.

## Routine deployment

```bash
cd /var/www/uat1/mini_ms_test
export REPO_ROOT=/var/www/uat1/mini_ms_test
```

| Change | Command | Behavior |
| --- | --- | --- |
| Frontend source | `bash deploy/scripts/prod-frontend-pm2.sh` | Runs npm ci and build, then restarts frontend on 3000 |
| Frontend already built | `bash deploy/scripts/prod-frontend-restart.sh` | Restarts preview using frontend/dist/ |
| Catalog/backend JAR | `bash deploy/scripts/prod-restart-isolated-catalog.sh` | Runs listed migrations, restarts catalog on 9184 and waits for health |
| Commerce JAR | `bash deploy/scripts/prod-restart-isolated-commerce.sh` | Restarts commerce on 9182 and checks connectivity |
| Gateway JAR | `bash deploy/scripts/prod-restart-isolated-gateway.sh` | Installs gateway JAR under deploy/, restarts gateway on 9180 and waits for health |
| Environment values | `bash deploy/scripts/prod-reload-env-and-restart.sh mini-prod-ms-commerce` | Loads prod.env and restarts the named PM2 service with --update-env |

Java helpers use existing JARs; build the changed module first. For example:

```bash
(cd backend && ./mvnw -Pprod -DskipTests package)
bash deploy/scripts/prod-restart-isolated-catalog.sh
```

Use `services/commerce` or `services/gateway` in place of `backend` for those modules. These build
commands skip tests; run relevant checks before deploying. Scripts deploy the
local source/artifacts and do not pull Git updates. Keep the previous artifacts
available for rollback; restart helpers stop the affected process during deployment.

`prod-deploy-frontend-from-laptop.sh` builds locally, uploads the complete frontend package and frontend/dist/ over SSH/rsync,
installs locked dependencies in frontend/, and restarts the remote frontend. Install local npm dependencies first and set
`REMOTE=user@server` and `REMOTE_DIR=/var/www/uat1/mini_ms_test` explicitly.

## Full stack and migrations

`prod-microservices-isolated-pm2.sh` starts catalog, commerce, store, and gateway
on 9184, 9182, 9181, and 9180 respectively. JARs come from `backend/target/`
and `services/{gateway,store,commerce}/target/`. It runs catalog migrations
before stopping services. It does not build JARs or deploy the frontend.
Integration code lives in `backend/`; no separate Integration JAR is required.

`run-catalog-liquibase.sh` and `catalog-isolated-migrations.list` support catalog
migrations; runtime Liquibase remains disabled. `lib/`,
`verify-spring-boot-fat-jar.sh` and `ensure-frontend-source.sh` are dependencies
of the retained scripts.

## Verification and maintenance

```bash
pm2 list
curl -fsS http://127.0.0.1:9184/management/health/liveness
curl -fsS http://127.0.0.1:9180/management/health/liveness
curl -fsS https://minisnmore.com/api/v1/categories
pm2 logs mini-prod-ms-catalog-core --nostream --lines 50
```

- `prod-diagnose-*`, `prod-find-nginx-config.sh`, `prod-verify-*`, and
  `verify-gateway-commerce-route-parity.sh` help investigate service configuration.
  Full-stack health scripts also probe Integration and report failure when it is absent.
- `prod-isolated-admin-smoke.sh` includes a Zoho retry-orders POST; it is not a
  read-only health check.
- `prod-fix-*`, `prod-hard-restart-gateway.sh`, `prod-nginx-*`, and
  `prod-apply-nginx-zoho-timeouts.sh` are incident/cutover tools, not routine deployments.
- SQL files, migration/patch helpers, Python import/repair/refund scripts,
  `prod-retire-mini-and-more.sh`, and `prod-toggle-razorpay-demo.sh` perform
  specific maintenance operations. Inspect their scope before running them.
- `prod-*-test.py`, mapping generators and image checks support catalog validation.
- `prod-run-ci-reports-*`, `prod-sync-ci-sources-from-laptop.sh`,
  `export-openapi.sh` and `setup-github-auth.sh` support reporting/tooling.

## Removed launchers

The cleanup removed 30 redundant or obsolete scripts:

- UAT shell launchers and their Nginx library, plus the root UAT frontend launcher.
- Old 808x production launchers and the associated preflight script.
- Gateway/all/isolated/unified laptop launchers that require missing Integration source.
- The redundant prod-isolated-deploy-on-server.sh wrapper; use the full-stack
  helper directly when its prerequisites are met.

Historical documents retain their original examples with a notice linking here.
Removed scripts remain available in Git history. Production maintenance and
database repair utilities were retained because infrequent use alone does not
establish that they are obsolete.
