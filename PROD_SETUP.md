# Production Setup Guide

> **Microservices (production):** Gateway **:8080** (public via Nginx), store **:8081**, commerce **:8082**, integration **:8083**, catalog core **:8084** (internal). Replaces the old monolith on **:8081** or **:8086**. See [docs/PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md), [deploy/scripts/preflight-prod-microservices.sh](deploy/scripts/preflight-prod-microservices.sh), and [deploy/scripts/prod-microservices-pm2.sh](deploy/scripts/prod-microservices-pm2.sh).

This document captures the production setup for Minis & More on a Hostinger VPS. It covers the Spring Boot backend, React/TanStack frontend, PostgreSQL database, PM2 processes, Nginx reverse proxy, SSL, Google OAuth, and environment variables.

## 1. Branch And Code

Use the `prod` branch for production deployment.

```bash
cd /var/www/blossom-blueprint-craft
git fetch origin
git switch prod
git pull origin prod
```

If the production branch does not exist on the server yet:

```bash
git fetch origin
git switch -c prod origin/prod
```

## 2. Required Ports

The production setup uses these local ports on the VPS:

| Service | Local Port | Public Access |
| --- | ---: | --- |
| Frontend preview server | `3005` | `https://minisnmore.com` |
| Backend Spring Boot app | `8086` | Proxied through `https://minisnmore.com/api/` |
| Reports, optional | Static Nginx alias | `https://minisnmore.com/reports/` |

Only ports `80` and `443` need to be public. Backend and frontend ports can remain internal.

## 3. PostgreSQL Production Database

Create the production database and user if they do not already exist.

```bash
sudo -u postgres psql
```

```sql
CREATE DATABASE miniandmore_prod;
CREATE USER miniandmore_produser WITH PASSWORD 'CHANGE_ME_PROD_DB_PASSWORD';
GRANT ALL PRIVILEGES ON DATABASE miniandmore_prod TO miniandmore_produser;
\c miniandmore_prod
GRANT ALL ON SCHEMA public TO miniandmore_produser;
ALTER SCHEMA public OWNER TO miniandmore_produser;
\q
```

If the production database was accidentally started with the `dev` profile or fake data, reset the schema before starting the backend again.

```bash
sudo -u postgres psql -d miniandmore_prod
```

```sql
DROP SCHEMA public CASCADE;
CREATE SCHEMA public;
ALTER SCHEMA public OWNER TO miniandmore_produser;
GRANT ALL ON SCHEMA public TO miniandmore_produser;
\q
```

After the reset, start the backend only with `prod,api-docs` profiles.

## 4. Backend Configuration

The backend production database values are configured through Spring configuration files and environment variables.

Expected datasource values:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/miniandmore_prod
    username: miniandmore_produser
    password: ${SPRING_DATASOURCE_PASSWORD:}
```

Do not commit real passwords, Google client secrets, JWT secrets, or other private values into Git.

## 5. Backend Build

Build the backend JAR from the repository root.

```bash
cd /var/www/blossom-blueprint-craft/backend
chmod +x mvnw
./mvnw -Pprod -DskipTests clean package
```

The JAR will be created under:

```bash
backend/target/*.jar
```

## 6. Backend PM2 Process

Install PM2 if it is not already installed.

```bash
npm install -g pm2
pm2 -v
```

Start the backend with environment variables passed from the terminal.

```bash
cd /var/www/blossom-blueprint-craft/backend

SPRING_DATASOURCE_PASSWORD='CHANGE_ME_PROD_DB_PASSWORD' \
GOOGLE_CLIENT_ID='CHANGE_ME_GOOGLE_CLIENT_ID' \
GOOGLE_CLIENT_SECRET='CHANGE_ME_GOOGLE_CLIENT_SECRET' \
JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET='CHANGE_ME_BASE64_JWT_SECRET' \
pm2 start "java -jar target/*.jar \
  --server.port=8086 \
  --spring.profiles.active=prod,api-docs \
  --server.forward-headers-strategy=framework \
  --spring.security.oauth2.client.registration.google.redirect-uri=https://minisnmore.com/login/oauth2/code/google" \
  --name mini-backend-prod
```

Save PM2 startup state.

```bash
pm2 save
pm2 startup
```

Common backend commands:

```bash
pm2 status
pm2 logs mini-backend-prod
pm2 restart mini-backend-prod --update-env
pm2 delete mini-backend-prod
```

Backend health check:

```bash
curl -I http://127.0.0.1:8086/management/health
curl http://127.0.0.1:8086/management/health
```

## 7. Frontend Build

Build the frontend using the production domain as the API base URL.

```bash
cd /var/www/blossom-blueprint-craft
npm ci
VITE_API_BASE_URL=https://minisnmore.com npm run build
```

The frontend should allow production hosts in `vite.config.ts`:

```ts
preview: {
  allowedHosts: ["uat.minisnmore.com", "minisnmore.com", "www.minisnmore.com"],
},
```

## 8. Frontend PM2 Process

Start the frontend preview server on port `3005`.

```bash
cd /var/www/blossom-blueprint-craft
VITE_API_BASE_URL=https://minisnmore.com \
pm2 start "npm run preview -- --host 127.0.0.1 --port 3005" \
  --name mini-frontend-prod
```

If the process already exists, restart it with updated environment variables.

```bash
cd /var/www/blossom-blueprint-craft
VITE_API_BASE_URL=https://minisnmore.com pm2 restart mini-frontend-prod --update-env
```

Frontend check:

```bash
curl -I http://127.0.0.1:3005
pm2 logs mini-frontend-prod
```

## 9. Nginx Production Site

Create or update the production Nginx site.

```bash
sudo nano /etc/nginx/sites-available/mini-frontend-prod
```

Use this configuration:

```nginx
server {
    listen 80;
    server_name minisnmore.com www.minisnmore.com;

    location / {
        return 301 https://$host$request_uri;
    }
}

server {
    listen 443 ssl http2;
    server_name minisnmore.com www.minisnmore.com;

    ssl_certificate /etc/letsencrypt/live/minisnmore.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/minisnmore.com/privkey.pem;
    include /etc/letsencrypt/options-ssl-nginx.conf;
    ssl_dhparam /etc/letsencrypt/ssl-dhparams.pem;

    # Admin image uploads (multipart) and other large API bodies — default nginx limit is 1m.
    client_max_body_size 10m;

    location /api/ {
        proxy_pass http://127.0.0.1:8086/api/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
    }

    location /oauth2/ {
        proxy_pass http://127.0.0.1:8086/oauth2/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
    }

    location /login/oauth2/ {
        proxy_pass http://127.0.0.1:8086/login/oauth2/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
    }

    location /v3/api-docs {
        proxy_pass http://127.0.0.1:8086/v3/api-docs;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
    }

    location /management/ {
        proxy_pass http://127.0.0.1:8086/management/;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
    }

    location /reports/ {
        alias /var/www/mini_reports/current/;
        index index.html;
        try_files $uri $uri/ =404;
    }

    location / {
        proxy_pass http://127.0.0.1:3005;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_set_header X-Forwarded-Port 443;
    }
}
```

Enable the site and reload Nginx.

```bash
sudo ln -s /etc/nginx/sites-available/mini-frontend-prod /etc/nginx/sites-enabled/mini-frontend-prod
sudo nginx -t
sudo systemctl reload nginx
```

If the symlink already exists, skip the `ln -s` command.

## 10. SSL Certificate

Install Certbot if needed.

```bash
sudo apt update
sudo apt install -y certbot python3-certbot-nginx
```

Generate or renew the certificate for both production domains.

```bash
sudo certbot --nginx -d minisnmore.com -d www.minisnmore.com
```

Verify the certificate:

```bash
sudo certbot certificates
curl -I https://minisnmore.com
```

If the browser shows `NET::ERR_CERT_COMMON_NAME_INVALID`, the certificate does not match the domain. Regenerate it with the correct `-d minisnmore.com -d www.minisnmore.com` domains.

## 11. Google OAuth Setup

In Google Cloud Console, configure the OAuth client for production.

Authorized redirect URIs:

```text
https://minisnmore.com/login/oauth2/code/google
https://www.minisnmore.com/login/oauth2/code/google
https://uat.minisnmore.com/login/oauth2/code/google
```

Authorized JavaScript origins (add UAT when testing social login on UAT):

```text
https://minisnmore.com
https://www.minisnmore.com
https://uat.minisnmore.com
```

UAT backend must have `GOOGLE_CLIENT_ID` and **`GOOGLE_CLIENT_SECRET`** set (PM2 / Jenkins deploy env). An empty secret causes Google sign-in to fail after account selection and redirect to `/login?error`.

After updating Google Cloud Console, restart the backend:

```bash
pm2 restart mini-backend-prod --update-env
```

Check the OAuth redirect:

```bash
curl -I https://minisnmore.com/oauth2/authorization/google
```

The `Location` header should contain:

```text
redirect_uri=https://minisnmore.com/login/oauth2/code/google
```

If it shows `http://minisnmore.com`, fix the Nginx HTTPS server block so every backend proxy location sends:

```nginx
proxy_set_header X-Forwarded-Proto https;
proxy_set_header X-Forwarded-Port 443;
```

### Apple Sign In

1. Create a **Services ID** and **Sign in with Apple** key in [Apple Developer](https://developer.apple.com/account/resources/identifiers/list/serviceId).
2. Set redirect URL: `https://minisnmore.com/login/oauth2/code/apple` (and `www` if used).
3. Generate a client secret JWT (expires every 6 months) and set backend env:

```bash
APPLE_CLIENT_ID=com.minisnmore.web
APPLE_CLIENT_SECRET=<generated-jwt>
```

### Facebook Login

1. In [Meta for Developers](https://developers.facebook.com/), create an app → **Facebook Login** (not “Facebook Login for Business”) → Web.
2. Under **Use cases** → **Authentication and account creation** → **Customize**, add permissions **`public_profile`** (required) and optionally **`email`** (must show **Ready for testing**). If you see *Invalid Scopes: email*, the `email` permission is not enabled on the app yet — the backend uses `public_profile` only until you add `email` in Meta and set `scope: public_profile,email` in `application-prod.yml`.
3. Valid OAuth redirect URI: `https://minisnmore.com/login/oauth2/code/facebook`
4. Set backend env to replace the mock placeholders in `application-prod.yml` (Jenkins can start with mocks; real sign-in needs real values):

```bash
FACEBOOK_CLIENT_ID=<app-id>
FACEBOOK_CLIENT_SECRET=<app-secret>
```

Set `APPLE_*` and `FACEBOOK_*` on the UAT/production backend (Jenkins deploy env or PM2) when social login should work for customers.

Restart the backend after adding credentials. Test:

```bash
curl -I https://minisnmore.com/oauth2/authorization/apple
curl -I https://minisnmore.com/oauth2/authorization/facebook
```

## 12. Zoho Books OAuth (API Console)

Create a **Server-based Application** at [Zoho API Console (India)](https://api-console.zoho.in/). Do not use Client-based — the backend stores `client_secret` and `refresh_token`.

Redirect URIs and JavaScript domains are configured in Spring (`application-prod.yml` → `application.zoho.oauth`). Defaults for UAT:

| Zoho field | Value |
| --- | --- |
| Homepage URL | `https://uat.minisnmore.com` |
| Authorized Redirect URIs | `https://uat.minisnmore.com`, `https://uat.minisnmore.com/admin/zoho` |
| JavaScript Domain | `https://uat.minisnmore.com` |

Production (`minisnmore.com`) — set env vars or override YAML before go-live:

```bash
export ZOHO_OAUTH_HOMEPAGE_URL=https://minisnmore.com
export ZOHO_OAUTH_REDIRECT_URI=https://minisnmore.com
export ZOHO_OAUTH_ADMIN_REDIRECT_URI=https://minisnmore.com/admin/zoho
export ZOHO_OAUTH_JAVASCRIPT_DOMAIN=https://minisnmore.com
```

**Client experience (recommended):** set these once on the UAT/production server (PM2 / Jenkins env). The store admin only clicks **Connect to Zoho Books** and signs in — no API console or token copying.

```bash
export ZOHO_CLIENT_ID=1000.xxxx          # example — use your Zoho Server-based client ID
export ZOHO_CLIENT_SECRET=xxxx           # example — inject from vault/secret manager, not git
export ZOHO_ORG_ID=60012345678           # example — your Zoho Books organisation ID (6–15 digits)
export ZOHO_DATA_CENTER=in
```

Restart the backend so Spring picks up the new variables (same pattern as section 6):

```bash
pm2 restart mini-backend-prod --update-env
```

If any Zoho client secret was ever committed to git, rotate it in the [Zoho API Console](https://api-console.zoho.in/) and update the server env / secret store only.

Ensure Zoho API Console redirect URI includes `https://uat.minisnmore.com/admin/zoho` (or prod equivalent).

**Manual fallback (UI, not an API call):** when `ZOHO_CLIENT_ID`, `ZOHO_CLIENT_SECRET`, and `ZOHO_ORG_ID` are unset, open the admin page **`/admin/zoho`** (e.g. `https://uat.minisnmore.com/admin/zoho`). The frontend automatically shows the legacy credential form instead of **Connect to Zoho Books**. Admins paste credentials there and click Save — no need to call `GET /api/v1/admin/zoho/oauth-setup` directly (that endpoint is for optional console hints when using manual mode).

### Admin sync (production UI)

After connect, store admins use **`/admin/zoho` → Catalogue & orders** (no Postman):

| Button | What it does |
| --- | --- |
| **Sync products to Zoho** | Pushes all **active** products to Zoho Books (each needs a **SKU** and **category** on the store; category name is added to the Zoho item description). |
| **Pull inventory from Zoho** | Updates store **stock** from Zoho for items already linked. |
| **Retry failed orders** | Re-sends paid orders that failed to sync (up to 25 per run). |
| **Pull order tracking** | Refreshes shipment/tracking from Zoho for open paid orders. |

**Automatic (no button):** paid checkout orders are sent to Zoho when payment succeeds; failed orders retry on a schedule (~10 min); tracking pulls on a schedule (~15 min).

**Typical go-live:** (1) Connect Zoho, (2) Test connection, (3) ensure every product has a **SKU** and **category**, (4) **Sync products to Zoho**, (5) optionally **Pull inventory** after stock changes in Zoho.

### Zoho invoice status webhook

Create one alphanumeric-only secret, 12-50 characters, and set the same value in the server environment and Zoho. Example:

```bash
ZOHO_INVOICE_WEBHOOK_SECRET=MNMZOHOinvoice2026A8F3K92Q
```

Restart the catalog/backend service after changing the environment.

In Zoho Books, create a webhook action:

| Field | Value |
| --- | --- |
| Path | Settings -> Automation -> Workflow Actions -> Webhooks -> New Webhook |
| Webhook name | `MNM Invoice Status Webhook` |
| Module | Invoices |
| Method | POST |
| UAT URL | `https://uat.minisnmore.com/api/v1/zoho/invoice-webhook` |
| Production URL | `https://minisnmore.com/api/v1/zoho/invoice-webhook` |
| Content type | JSON / `application/json` |
| Secure this webhook | Yes |
| Secret token | Same value as `ZOHO_INVOICE_WEBHOOK_SECRET`, alphanumeric only |
| Custom header | `X-Zoho-Invoice-Secret` |
| Custom header value | Same value as `ZOHO_INVOICE_WEBHOOK_SECRET` |

Use this user-defined JSON body. Insert the actual Zoho placeholders from the Invoice placeholder picker, but keep these JSON keys unchanged:

```json
{
  "eventId": "${INVOICE.INVOICE_ID}${INVOICE.STATUS}${INVOICE.LAST_MODIFIED_TIME}",
  "invoice": {
    "invoice_id": "${INVOICE.INVOICE_ID}",
    "invoice_number": "${INVOICE.INVOICE_NUMBER}",
    "salesorder_id": "${INVOICE.SALESORDER_ID}",
    "reference_number": "${INVOICE.REFERENCE_NUMBER}",
    "status": "${INVOICE.STATUS}",
    "last_modified_time": "${INVOICE.LAST_MODIFIED_TIME}"
  }
}
```

Create the workflow rule:

| Field | Value |
| --- | --- |
| Path | Settings -> Automation -> Workflow Rules -> New Workflow Rule |
| Workflow rule name | `MNM Send Invoice Status to Website` |
| Module | Invoices |
| Workflow type | Event Based |
| Trigger | Created or Edited |
| Execute when | Status is changed, or Any field is edited if Status is changed is unavailable |
| Action type | Webhook |
| Action | `MNM Invoice Status Webhook` |

Criteria must use **OR**, not AND:

```text
Status is Paid
OR Status is Sent
OR Status is Void
OR Status is Voided
```

Do not configure `Paid AND Sent AND Void`; one invoice cannot have multiple statuses at the same time, so that rule will never fire.

## 13. Firewall

Allow public web traffic.

```bash
sudo ufw allow OpenSSH
sudo ufw allow 80
sudo ufw allow 443
sudo ufw status
```

Do not expose backend port `8086` or frontend port `3005` publicly unless there is a specific reason.

## 14. Reports Hosting

Jenkins can publish generated reports to:

```text
/var/www/mini_reports/current
```

Create the reports folder if needed.

```bash
sudo mkdir -p /var/www/mini_reports/releases
sudo chown -R "$USER":"$USER" /var/www/mini_reports
```

Reports are served through:

```text
https://minisnmore.com/reports/
```

The Nginx location is:

```nginx
location /reports/ {
    alias /var/www/mini_reports/current/;
    index index.html;
    try_files $uri $uri/ =404;
}
```

## 15. Verification Checklist

Run these checks after deployment.

```bash
pm2 status
curl -I https://minisnmore.com
curl -I https://minisnmore.com/api/account
curl https://minisnmore.com/management/health
curl -I https://minisnmore.com/oauth2/authorization/google
sudo nginx -t
sudo systemctl status nginx
```

Expected results:

- `https://minisnmore.com` returns the frontend.
- `/api/*` routes proxy to the backend.
- `/management/health` returns backend health JSON.
- Google OAuth redirects with an HTTPS `redirect_uri`.
- PM2 shows `mini-frontend-prod` and `mini-backend-prod` online.

## 16. UAT deploy still shows old homepage?

Jenkins **Deploy UAT** must run with **BUILD_FRONTEND** and **BUILD_BACKEND** enabled. The pipeline now runs `npm run build` on the VPS after each rsync so PM2 does not serve a stale `dist/`.

After deploy, verify the live release:

```bash
curl -fsS https://uat.minisnmore.com/RELEASE.txt
# First line = Jenkins BUILD_NUMBER, second line = git commit SHA
```

If that file is missing or shows an old build number, PM2 is still on a previous release under `/var/www/uat1/mini_and_more/current`.

On the VPS:

```bash
cd /var/www/uat1/mini_and_more/current
cat dist/RELEASE.txt
pm2 logs mini-uat-frontend --lines 30
pm2 describe mini-uat-frontend
```

Hard-refresh the browser (`Cmd+Shift+R`). Ensure Nginx includes `location /` from `deploy/nginx/uat.minisnmore.com.locations.conf` with `Cache-Control: no-store` for HTML.

### R2 image upload (admin)

Admin uploads use **`POST /api/v1/admin/uploads`** (file through the backend + AWS SDK). Ensure nginx `client_max_body_size` is at least `10m` on the `/api/` server block.

**Broken images after save** usually mean `S3_PUBLIC_BASE_URL` is missing or wrong. The API must return URLs on your **public** host, not `*.r2.cloudflarestorage.com` (that host is upload-only).

In `/etc/mini-and-more/uat.env` (and prod):

```bash
# Public bucket URL from Cloudflare R2 → bucket → Settings → Public access (r2.dev)
S3_PUBLIC_BASE_URL=https://pub-xxxxxxxx.r2.dev
# Must match the bucket where objects are stored
S3_BUCKET=miniandmore
S3_KEY_PREFIX=uat
```

Enable **public access** on the R2 bucket, then open a test object URL in the browser before using admin upload.

Console **404** on `/__l5e/assets-v1/*.mp4` is expected on self-hosted UAT: those URLs are Lovable CDN placeholders. Upload videos to R2/S3 and reference them in admin, or leave Watch & Shop / fabric video disabled in **Admin → Home**.

## 16. UAT OpenAPI (`uat.minisnmore.com`)

Jenkins deploy should run **gateway** (`mini-uat-gateway`, port **8080**, public) and **catalog** (`mini-uat-catalog`, port **8081**, internal). Use `deploy/scripts/uat-microservices-pm2.sh` after building both JARs. OpenAPI on the VPS:

```text
http://127.0.0.1:8080/v3/api-docs
```

Jenkins and browsers need the **public** URL:

```text
https://uat.minisnmore.com/v3/api-docs
```

If that URL returns **404**, Nginx is sending `/v3/api-docs` to the frontend (Vite) instead of the backend. Copy the locations from `deploy/nginx/uat.minisnmore.com.locations.conf` into the UAT HTTPS server block **before** `location / { ... }`.

On the VPS:

```bash
sudo nano /etc/nginx/sites-available/<uat-site-config>
# paste / adjust locations from deploy/nginx/uat.minisnmore.com.locations.conf
# ensure /api/, /oauth2/, and /v3/api-docs proxy to 127.0.0.1:8080 (gateway)
sudo nginx -t
sudo systemctl reload nginx
```

Verify:

```bash
curl -fsS https://uat.minisnmore.com/management/health
curl -fsS https://uat.minisnmore.com/v3/api-docs | head -c 200
curl -I https://uat.minisnmore.com/swagger-ui/index.html
pm2 list   # expect mini-uat-gateway :8080 and mini-uat-catalog :8081; remove legacy mini-uat / mini-uat-backend
```

Optional: browse Swagger UI at `https://uat.minisnmore.com/swagger-ui/index.html`.

## 16. Common Issues

### `Bad value for type long`

Cause: the production database likely contains fake data inserted by the `dev` profile.

Fix: reset the `public` schema as the `postgres` user, then restart backend with only `prod,api-docs` profiles.

### `redirect_uri_mismatch`

Cause: Google OAuth does not have the exact production redirect URI, or backend is generating `http` instead of `https`.

Fix:

- Add `https://minisnmore.com/login/oauth2/code/google` in Google Cloud Console.
- Ensure Nginx sends `X-Forwarded-Proto https` and `X-Forwarded-Port 443`.
- Ensure backend starts with `--server.forward-headers-strategy=framework`.

### `NET::ERR_CERT_COMMON_NAME_INVALID`

Cause: SSL certificate was created for another domain.

Fix:

```bash
sudo certbot --nginx -d minisnmore.com -d www.minisnmore.com
```

### Vite Host Blocked

Cause: the production hostname is not allowed by Vite preview.

Fix: add `minisnmore.com` and `www.minisnmore.com` to `preview.allowedHosts` in `vite.config.ts`, rebuild, and restart the frontend PM2 process.

### Backend Cannot Connect To Database

Check:

- `SPRING_DATASOURCE_PASSWORD` is passed to the PM2 start command.
- Database name is `miniandmore_prod`.
- Database user is `miniandmore_produser`.
- Backend is running with `prod,api-docs`, not `dev`.

Restart with updated environment:

```bash
pm2 restart mini-backend-prod --update-env
```
