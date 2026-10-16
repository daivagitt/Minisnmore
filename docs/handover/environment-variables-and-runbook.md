# Environment variables and runbook

**File on the server:** `/etc/mini-and-more/prod.env` (`chmod 600`, root only)  
**Template in git:** `deploy/env/prod.env.example`  
**Do not commit real values.**

Java processes do not re-read this file until they are started with `--update-env` (the prod restart scripts do that).

Live isolated stack **overrides** some example defaults:

| Variable | Live isolated value |
| --- | --- |
| `APPLICATION_CATALOG_BASE_URL` | `http://127.0.0.1:9184` |
| `MINIANDMORE_STORE_URI` | `http://127.0.0.1:9181` |
| `MINIANDMORE_COMMERCE_URI` | `http://127.0.0.1:9182` |
| `MINIANDMORE_INTEGRATION_URI` | `http://127.0.0.1:9183` |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://127.0.0.1:5432/miniandmore` |
| `SPRING_R2DBC_URL` | `r2dbc:postgresql://127.0.0.1:5432/miniandmore` |
| `S3_KEY_PREFIX` | `prod` |
| `VITE_API_BASE_URL` / mail / CORS / Zoho OAuth | `https://minisnmore.com` |
| `LOG_JSON` | `false` on this host (JSON encoder not on all classpaths) |
| `GATEWAY_RESPONSE_TIMEOUT` | `600s` (Zoho admin) |

`prod.env.example` still shows `:8084` and a separate `gateway` database. That is the **8080 layout**. This VPS uses the **918x isolated** layout above.

## Variable groups

### Must be set or the stack will not boot

| Name | Used by |
| --- | --- |
| `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET` | All Java services (same value) |
| `INTERNAL_API_KEY` | Catalog ↔ integration / internal admin |
| `SPRING_DATASOURCE_*` | Catalog JDBC |
| `SPRING_R2DBC_*` / `SPRING_LIQUIBASE_*` | Gateway users (same DB on this host) |
| `RAZORPAY_KEY_ID` / `RAZORPAY_KEY_SECRET` | Catalog prod validator |

### Public URLs

`JHIPSTER_MAIL_BASE_URL`, `FRONTEND_AUTH_CALLBACK_URL`, `CORS_ALLOWED_ORIGIN_PATTERNS`, `ZOHO_OAUTH_*`, `VITE_API_BASE_URL`.

### Mail

`SPRING_MAIL_PROVIDER` = `gmail` or `zoho`. Provider-specific `GMAIL_MAIL_*` / `ZOHO_MAIL_*` are copied onto `SPRING_MAIL_*` by `deploy/scripts/lib/gateway-env.sh`.

### Social login

`GOOGLE_*`, `APPLE_*`, `FACEBOOK_*`. Empty values are replaced with a placeholder at gateway start so the JVM boots; that provider’s login will fail until real ids are set.

### Object storage (Cloudflare R2)

`S3_ENDPOINT`, `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PUBLIC_BASE_URL`, `S3_KEY_PREFIX=prod`, `S3_MAX_UPLOAD_BYTES`.

### Zoho

`ZOHO_CLIENT_ID` / `SECRET` / `ORG_ID` / `DATA_CENTER=in`, sync flags, `ZOHO_*_WEBHOOK_SECRET` (see [zoho-webhooks-and-details.md](./zoho-webhooks-and-details.md)).

### Other

`LEDGERLY_*` — present in env; catalog can run Ledgerly beside Zoho.  
`FASTSMS_*` — OTP SMS.

### XpressBees

| Name | Purpose |
| --- | --- |
| `XPRESSBEES_BASE_URL` | Carrier API base URL |
| `XPRESSBEES_EMAIL`, `XPRESSBEES_PASSWORD` | Token-login credentials |
| `XPRESSBEES_XB_KEY` | Legacy/key-based API credential when the configured auth mode requires it |
| `XPRESSBEES_WEBHOOK_SECRET` | HMAC/shared secret for shipment and return callbacks |
| `XPRESSBEES_WAREHOUSE_*` | Pickup/contact address sent on shipment creation |
| `XPRESSBEES_ORIGIN_PINCODE` | Origin used for serviceability and shipment requests |
| `XPRESSBEES_PICKUP_VENDOR_CODE` | Provider pickup location identifier, when assigned |

Changing courier credentials or webhook secret requires a catalog restart. Never put the real values in `deploy/env/prod.env.example`.

## Runbook — start of day / incident

1. `pm2 list` — all `mini-prod-ms-*` and `mini-prod-frontend` **online**.
2. Hit `http://127.0.0.1:9180/management/health/liveness` and `:9181`, `:9182`, `:9184`.
3. Hit `https://minisnmore.com/api/v1/categories`.
4. If API is 502/504: store or catalog is down — restart catalog first, then store, then gateway.
5. If HTML is 502 but API works: restart `mini-prod-frontend` only.
6. If OAuth fails: check `GOOGLE_CLIENT_*` and callback `https://minisnmore.com/auth/callback`.
7. After editing `prod.env`:  
   `bash /var/www/uat1/mini_ms_test/deploy/scripts/prod-reload-env-and-restart.sh`

## Runbook — planned bounce (one service)

Catalog first if the JAR or schema changed, then store, commerce, gateway. Frontend last if `frontend/dist/` changed.

```bash
cd /var/www/uat1/mini_ms_test
bash deploy/scripts/prod-restart-isolated-catalog.sh
bash deploy/scripts/prod-restart-isolated-commerce.sh
bash deploy/scripts/prod-restart-isolated-gateway.sh
```

Wait for `OK` in each script. Gateway often needs ~30s–3 min.

## Runbook — env missing

If `/etc/mini-and-more/prod.env` is gone, restore from vault. A leftover `/etc/mini-and-more/uat.env` may still exist as a **backup copy** from the rename; new scripts do not source it. Prefer `prod.env` only.
