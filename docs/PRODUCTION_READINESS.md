> Historical deployment instructions: obsolete UAT, 808x, and Integration-dependent laptop launchers have been removed. Use the [current script index](../deploy/scripts/README.md) for supported commands.

# Production readiness — microservices

Checklist for running **gateway** + **catalog** at production quality.

## Architecture

| Component | Port | Exposure |
|-----------|------|----------|
| Nginx | 443 | Public |
| Gateway (`services/gateway/`) | 9180 | `127.0.0.1` only |
| Store (`services/store/`) | 9181 | `127.0.0.1` only |
| Commerce (`services/commerce/`) | 9182 | `127.0.0.1` only |
| Catalog core (`backend/`) | 9184 | `127.0.0.1` only |
| Consul | 8500 | `127.0.0.1` only |
| Postgres | 5432+ | `127.0.0.1` only |
| Redis | 6379 | `127.0.0.1` only |

## Security

- [ ] JWT secret from env (`JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`) — same on gateway, catalog, Consul central config
- [ ] No secrets in Git; use `deploy/env/prod.env.example` → `/etc/mini-and-more/prod.env` (`chmod 600`)
- [ ] Catalog binds `127.0.0.1` (`SERVER_ADDRESS` in prod profile)
- [ ] Nginx: `deploy/nginx/prod.minisnmore.com.locations.conf` + `security-headers.conf`
- [ ] Production: block public `/management/` (see prod nginx snippet)
- [ ] OAuth client IDs/secrets only in env on gateway
- [ ] CORS: `CORS_ALLOWED_ORIGIN_PATTERNS` lists real front-end origins only

## Resilience

- [ ] Graceful shutdown (`server.shutdown: graceful`, 30s phase timeout)
- [ ] Gateway → catalog timeouts: 5s connect, 120s response
- [ ] Hikari pool tuned (`HIKARI_MAX_POOL_SIZE`, etc.)
- [ ] Health probes: `/management/health/liveness`, `/management/health/readiness`
- [ ] Run `deploy/scripts/health-check-microservices.sh` after deploy

## Observability

- [ ] JSON logs in prod (`LOG_JSON=true`)
- [ ] Prometheus optional (`PROMETHEUS_ENABLED=true` on internal scrape)
- [ ] Tracing sample rate 5–10% (`TRACING_SAMPLE_PROBABILITY`)

## Validate and build

From the repository root:

```bash
npm --prefix frontend ci
npm --prefix frontend run check
bash scripts/verify-deployment-layout.sh
for dir in backend services/gateway services/store services/commerce; do
  (cd "$dir" && ./mvnw test)
done
npm --prefix frontend run build
(cd backend && ./mvnw -Pprod -DskipTests package)
(cd services/gateway && ./mvnw -Pprod -DskipTests package)
(cd services/store && ./mvnw -DskipTests package)
(cd services/commerce && ./mvnw -DskipTests package)
```

The lightweight store/commerce projects use runtime Spring profiles; they do not declare a Maven `prod` profile. The core and gateway retain their generated Maven profiles.

## Deployment

Use the [current script index](../deploy/scripts/README.md). `REPO_ROOT` identifies the repository, while frontend PM2 uses `$REPO_ROOT/frontend` as its working directory.

After staging validation, select the restart helper for the changed module:

```bash
export REPO_ROOT=/path/to/release
bash deploy/scripts/prod-restart-isolated-catalog.sh
bash deploy/scripts/prod-restart-isolated-commerce.sh
bash deploy/scripts/prod-restart-isolated-gateway.sh
bash deploy/scripts/prod-frontend-pm2.sh
```

The full four-service launcher is `prod-microservices-isolated-pm2.sh`. It reads JARs from `backend/target/` and `services/*/target/`, applies the catalog migrations listed in `catalog-isolated-migrations.list`, and starts catalog, store, commerce, and gateway. Integration logic runs inside core.

The production frontend listens on localhost `3000`; Nginx forwards API traffic to gateway `9180`. Store `9181`, commerce `9182`, and core `9184` remain internal. The isolated production Nginx snippet is `deploy/nginx/prod.minisnmore.com.isolated-9180.locations.conf`; static files are under `frontend/public/`. Validate the site's assembled configuration with `nginx -t` before reloading it.

Deployment helpers load the existing `/etc/mini-and-more/prod.env`. Use separate configuration, databases, gateway credentials, payment test keys, and mail capture on a staging host. The historical `uat1` directory on this server is production, not an isolated test environment.

## Runtime profiles

| Component | Production profile |
| --- | --- |
| Gateway | `prod,prod-isolated,api-docs` |
| Catalog core | `prod,api-docs` |
| Store and commerce | `prod` |

Do not enable `faker` or seed-data migrations against production databases.

## Post-deployment checks

```bash
bash deploy/scripts/health-check-microservices.sh
bash deploy/scripts/prod-verify-isolated-stack.sh
bash scripts/verify-frontend-api-contract.sh
```

On staging, validate registration/login, catalog and product variants, cart and coupons, a payment in test mode, admin actions, return/refund workflows, and email delivery into a test mailbox. Record that evidence before production rollout.

## Frontend contract

All React API paths must match [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md).  
After deploy: `bash scripts/verify-frontend-api-contract.sh`.

## Module testing

After deploy, verify modules in order: [MODULES_TESTING.md](./MODULES_TESTING.md).

## CI

Use `frontend/package-lock.json` with npm, and run `npm --prefix frontend run check` plus each module’s Maven test suite before packaging release artifacts. The report helpers under `jenkins/` read frontend output from `frontend/coverage/` and service output from `services/*/target/`.
