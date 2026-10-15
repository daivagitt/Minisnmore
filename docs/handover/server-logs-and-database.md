# Server logs and database — what to check

## Need

You **do** need log + database access for production support:

- PM2 / Nginx logs: why a request 502’d, why Zoho returned 401, why JWT failed.
- Postgres `miniandmore`: catalog, orders, coupons, webhook event tables, **and** gateway users on this host.

You do **not** need the extra `gateway` database that still exists on this VPS — live R2DBC points at `miniandmore`. You do **not** need `ledgerly_*` databases for Mini’s n More.

## Logs

| Source | Path / command | When |
| --- | --- | --- |
| PM2 stdout/err | `/root/.pm2/logs/mini-prod-*.log` | JVM crash, Spring exceptions |
| Follow live | `pm2 logs mini-prod-ms-catalog-core --lines 200` | Deploy / webhook test |
| Nginx access | `/var/log/nginx/access.log` | Did Zoho / Razorpay / the browser hit us? |
| Nginx error | `/var/log/nginx/error.log` | Upstream timeout, SSL, 502 |
| systemd nginx | `journalctl -u nginx -n 50` | Reload failures |
| Certbot | `journalctl -u certbot` / `certbot certificates` | TLS expiry |

Frontend logs are mostly the Vite preview process (`mini-prod-frontend`). SPA errors also appear in the **browser console**.

`LOG_JSON=false` on this host so PM2 files stay line-oriented.

### What a healthy access line looks like

```text
GET /api/v1/categories → 200
GET /shop → 307 or 200
POST /api/v1/zoho/invoice-webhook → 200
POST /internal/v1/zoho/shipment-webhook → 200
POST /api/v1/webhooks/xpressbees/shipments → 200
POST /api/v1/webhooks/xpressbees/returns → 200
```

`401` on `/api/v1/order-returns` without a cookie is expected.

### First places to grep

```bash
# Catalog / Zoho
grep -iE 'zoho|webhook|ERROR' /root/.pm2/logs/mini-prod-ms-catalog-core-out.log | tail -50

# Auth
grep -iE 'jwt|oauth|ERROR' /root/.pm2/logs/mini-prod-ms-gateway-out.log | tail -50

# Payments
grep -iE 'razorpay|ERROR' /root/.pm2/logs/mini-prod-ms-catalog-core-out.log | tail -50

# XpressBees forward/reverse callbacks
grep -iE 'xpressbees|expressbees|webhook|ERROR' /root/.pm2/logs/mini-prod-ms-catalog-core-out.log | tail -50
```

Disk: if `/` fills up, PM2 logs are the usual cause. Rotate or truncate old `~/.pm2/logs/*` after copying an incident snippet. Do not delete `/var/log/nginx` without logrotate.

## Postgres

| Database | Port | Used by live MiniMS? |
| --- | --- | --- |
| `miniandmore` | 5432 | **Yes** — catalog JDBC + gateway R2DBC |
| `gateway` | 5432 | **No** (leftover; do not drop until confirmed unused) |
| `ledgerly_*` | 5432 | **No** — other product on this VPS |

Connect using `SPRING_DATASOURCE_USERNAME` / `PASSWORD` from `prod.env` (catalog) or `SPRING_R2DBC_*` (same DB here).

### Checks that matter

```sql
-- catalog up
SELECT count(*) FROM product;
SELECT count(*) FROM customer_order;

-- recent webhook processing
SELECT * FROM zoho_shipment_webhook_event ORDER BY id DESC LIMIT 20;
SELECT * FROM zoho_invoice_webhook_event ORDER BY id DESC LIMIT 20;
SELECT * FROM xpressbees_shipment_webhook_event ORDER BY id DESC LIMIT 20;
SELECT * FROM xpressbees_return_webhook_event ORDER BY id DESC LIMIT 20;

-- stuck orders (adjust names if schema differs)
SELECT id, order_number, status, created_date
FROM customer_order
ORDER BY created_date DESC
LIMIT 20;
```

Liquibase: runtime auto-migrate is **off**. Pending changesets are applied with `deploy/scripts/run-catalog-liquibase.sh` **before** swapping the catalog JAR. After a successful run you should see **0 pending**.

### Backup (recommended)

```bash
# run as postgres or with prod.env credentials
pg_dump -Fc -h 127.0.0.1 -U "$SPRING_DATASOURCE_USERNAME" -d miniandmore \
  -f "/var/backups/miniandmore-$(date +%F).dump"
```

Store dumps **off this VPS**. Restore only onto a test copy of `miniandmore`, never onto `ledgerly_*`.

Images are **not** in Postgres; they are in Cloudflare R2 (`S3_BUCKET` + prefix `prod`). A DB restore without R2 still has broken product photos.

## Redis

`127.0.0.1:6379` — JHipster cache for catalog. If Redis is down, catalog may still boot depending on config but will be slower / error on cache. Check: `redis-cli ping` → `PONG`.

## Quick health matrix

| Symptom | Check |
| --- | --- |
| Blank shop / 502 HTML | `pm2 list` frontend; nginx error log |
| 502 on `/api` | gateway + store + catalog liveness; catalog logs |
| Login works, shop empty | catalog DB `product` / `active`; R2 URLs |
| Checkout pays but order missing | catalog `customer_order`; Razorpay dashboard; commerce logs |
| Zoho tracking never updates | Nginx POST to shipment webhook; `zoho_shipment_webhook_event`; Zoho workflow history |
| XpressBees tracking never updates | Nginx POST to the shipment/return callback; matching `xpressbees_*_webhook_event`; active AWB |
| Invoice PDF missing | invoice webhook 200; catalog `:9184` was up |
| Admin Zoho timeout | nginx 600s location; catalog still running the sync |
