# Deployment guide

This host is **production**. Public traffic is Nginx → frontend `:3000` and API gateway `:9180`.

Do not point scripts at `uat.minisnmore.com` or `/etc/mini-and-more/uat.env`. Use `/etc/mini-and-more/prod.env`.

## Layout on the VPS

| Item | Value |
| --- | --- |
| App root | `/var/www/uat1/mini_ms_test` (path name is historical; this **is** prod) |
| Env file | `/etc/mini-and-more/prod.env` (`chmod 600`) |
| Nginx | `/etc/nginx/sites-available/mini-prod.conf` → `sites-enabled/mini-prod.conf` |
| API snippet | `deploy/nginx/prod.minisnmore.com.isolated-9180.locations.conf` |
| Gateway JAR used by PM2 | `deploy/gateway-prod.jar` |
| Git | `origin` = `https://github.com/developersqobox-org/mini_and_more.git`, branch `prod` |

## What is running

```text
mini-prod-frontend          :3000
mini-prod-ms-gateway        :9180
mini-prod-ms-store          :9181
mini-prod-ms-commerce       :9182
mini-prod-ms-catalog-core   :9184
```

`pm2 save` writes `/root/.pm2/dump.pm2` so processes come back after reboot (`pm2 startup` should already be enabled).

## Build artefacts

| Service | Typical JAR / output |
| --- | --- |
| Catalog | `backend/target/miniandmore-*.jar` |
| Store | `store/target/miniandmore-store-*.jar` |
| Commerce | `commerce/target/miniandmore-commerce-*.jar` |
| Gateway | `gateway/target/gateway-*.jar` copied to `deploy/gateway-prod.jar` |
| Frontend | `npm ci && npm run build` → `dist/` |

Java: `./mvnw -Pprod -DskipTests package` in each module. Runtime Liquibase is disabled; apply schema **before** swapping the catalog process:

```bash
# uses SPRING_DATASOURCE_* from prod.env
set -a && . /etc/mini-and-more/prod.env && set +a
bash /var/www/uat1/mini_ms_test/deploy/scripts/run-catalog-liquibase.sh \
  /var/www/uat1/mini_ms_test/backend/target/miniandmore-0.0.1-SNAPSHOT.jar
```

## Restart one service (preferred)

Always wait for `/management/health/liveness` before moving on.

| Service | Script |
| --- | --- |
| Catalog | `bash deploy/scripts/prod-restart-isolated-catalog.sh` |
| Commerce | `bash deploy/scripts/prod-restart-isolated-commerce.sh` |
| Gateway | `bash deploy/scripts/prod-restart-isolated-gateway.sh` |
| Frontend (rebuild) | `bash deploy/scripts/prod-frontend-pm2.sh` |
| Frontend (`dist/` already built) | `bash deploy/scripts/prod-frontend-restart.sh` |
| Reload env into running JVMs | `bash deploy/scripts/prod-reload-env-and-restart.sh` |

Store has no dedicated helper in all cases; start it the same way as commerce (prod profile, `APPLICATION_CATALOG_BASE_URL=http://127.0.0.1:9184`, port `9181`) or use the isolated stack script.

Scripts delete **both** `mini-prod-ms-*` and legacy `mini-uat-ms-*` names so a bounce cannot leave two processes on the same port.

## Full isolated stack

```bash
REPO_ROOT=/var/www/uat1/mini_ms_test bash deploy/scripts/prod-microservices-isolated-pm2.sh
```

This recycles **9180–9184 only**. It does not touch `:3000`. Boot can take several minutes per JVM. Health check:

```bash
GATEWAY_URL=http://127.0.0.1:9180 \
STORE_URL=http://127.0.0.1:9181 \
COMMERCE_URL=http://127.0.0.1:9182 \
CATALOG_CORE_URL=http://127.0.0.1:9184 \
  bash deploy/scripts/health-check-microservices.sh
```

(The health script also probes integration `:9183`. If integration is not deployed, that check fails even when the shop is fine.)

## Nginx

```bash
nginx -t && systemctl reload nginx
```

Public API must keep proxying `/api/` to `127.0.0.1:9180` except invoice webhooks, which go to `:9184` (see [zoho-webhooks-and-details.md](./zoho-webhooks-and-details.md)).

## Laptop → server (optional)

`deploy/scripts/prod-deploy-frontend-from-laptop.sh` and `prod-deploy-gateway-from-laptop.sh` rsync build output then run the restart scripts on the VPS. Confirm `REMOTE_DIR=/var/www/uat1/mini_ms_test`.

## After every deploy

1. `curl -fsS https://minisnmore.com/api/v1/categories` → JSON `200`
2. Homepage and `/shop` load products
3. `pm2 list` — all `mini-prod-*` **online**, no `errored`
4. `pm2 save`

See [post-deployment-support.md](./post-deployment-support.md) for the support window and rollback.
