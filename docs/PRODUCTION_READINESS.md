# Production readiness — microservices

Checklist for running **gateway** + **catalog** at production quality.

## Architecture

| Component | Port | Exposure |
|-----------|------|----------|
| Nginx | 443 | Public |
| Gateway | 8080 | `127.0.0.1` only |
| Store | 8081 | `127.0.0.1` only |
| Commerce | 8082 | `127.0.0.1` only |
| Integration | 8083 | `127.0.0.1` only |
| Catalog core | 8084 | `127.0.0.1` only |
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

## Deploy (replace monolith on :8081 / :8086)

```bash
# 1. Build all five JARs (local or CI)
for dir in gateway backend store commerce integration; do
  (cd "$dir" && ./mvnw -Pprod -DskipTests clean package)
done

# 2. On VPS — env + preflight
sudo cp deploy/env/prod.env.example /etc/mini-and-more/prod.env
sudo chmod 600 /etc/mini-and-more/prod.env
# Fill secrets; reuse existing prod DB password and JWT secret from monolith.

sudo cp deploy/nginx/prod.minisnmore.com.locations.conf /etc/nginx/snippets/minisnmore-prod-locations.conf
sudo nginx -t && sudo systemctl reload nginx

chmod +x deploy/scripts/preflight-prod-microservices.sh deploy/scripts/prod-microservices-pm2.sh
REPO_ROOT=/var/www/blossom-blueprint-craft bash deploy/scripts/preflight-prod-microservices.sh

# 3. Start stack (stops legacy mini-backend-prod and binds :8080–8084)
ENV_FILE=/etc/mini-and-more/prod.env REPO_ROOT=/var/www/blossom-blueprint-craft \
  bash deploy/scripts/prod-microservices-pm2.sh
```

### Production frontend

Build and restart the storefront/admin UI from the production checkout:

```bash
REPO_ROOT=/var/www/uat1/mini_ms_test \
  bash deploy/scripts/prod-frontend-pm2.sh
```

The production frontend uses `https://minisnmore.com` by default and runs as
`mini-prod-frontend` on localhost port `3000`. UAT frontend scripts retain
their separate `uat.minisnmore.com` defaults.

**Port layout:** Nginx public → gateway **8080** only. Store **8081**, commerce **8082**, integration **8083**, catalog **8084** are localhost-only. Do not expose edge ports publicly.

UAT1 (`/var/www/uat1/mini_and_more`): Jenkins `DEPLOY_UAT` rsyncs all JARs; on the VPS run `deploy/scripts/uat-microservices-pm2.sh` (see `deploy/env/uat.env.example`). Gateway profile `uat` uses localhost routes to edges.

## Profiles

| Environment | Gateway | Catalog |
|-------------|---------|---------|
| Local dev | `dev` | `dev` |
| UAT | `prod,api-docs,uat` | `prod,api-docs` (core); edges `prod` |
| Production | `prod` (+ `consul` if using config server) | `prod` |

Do not use `dev` or `faker` Liquibase contexts in production.

## Frontend contract

All React API paths must match [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md).  
After deploy: `bash scripts/verify-frontend-api-contract.sh`.

## Module testing

After deploy, verify modules in order: [MODULES_TESTING.md](./MODULES_TESTING.md).

## CI

Jenkins: `BUILD_GATEWAY`, `BUILD_BACKEND`, `BUILD_STORE`, `BUILD_COMMERCE`, `BUILD_INTEGRATION`, `DEPLOY_UAT` — see root `Jenkinsfile` (UAT1 → `REMOTE_APP_DIR=/var/www/uat1/mini_and_more`).
