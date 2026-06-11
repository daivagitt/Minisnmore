# Microservices architecture (JHipster 9)

This branch runs a **JHipster microservice stack** for Minis & More:

| App | Folder | Port | Role |
|-----|--------|------|------|
| **Gateway** | `gateway/` | **8080** | JWT login/register, Spring Cloud Gateway, Consul |
| **Catalog** | `backend/` (`miniandmore`) | **8081** | Store entities (products, orders, Zoho settings, …) |

JHipster puts **users and authentication on the gateway** (not a separate `userservice` app). The catalog service uses `skipUserManagement` and stores `userLogin` on orders/profiles instead of a local `User` table.

## Request flow

```text
Browser (Vite :5173)
  → proxy /api → Gateway :8080
       → /api/authenticate, /api/register, /api/account/*  (handled on gateway)
       → /api/v1/**                                          (routed to miniandmore via Consul)
  → /services/miniandmore/**  (discovery locator, optional)
```

## Local development

### 1. Infrastructure

```bash
docker compose -f docker/microservices.yml up -d
```

Starts Consul (`8500`), Postgres for catalog (`5432`), Postgres for gateway (`5433`), Redis (`6379`).

Disable Spring Boot embedded compose if you use the shared file (avoids duplicate Postgres):

```yaml
# gateway/src/main/resources/config/application.yml & backend equivalent
spring:
  docker:
    compose:
      enabled: false
```

### 2. Start services (order matters)

```bash
# One-shot (Docker + catalog + gateway)
bash scripts/dev-start-microservices.sh

# Or manually:
# Terminal 1 – catalog microservice
cd backend && ./mvnw

# Terminal 2 – gateway (after Consul is up)
cd gateway && ./mvnw
```

### 3. Frontend

```bash
npm run dev
```

Vite proxies `/api` to **8080** (gateway). CORS is enabled on the gateway for `http://localhost:5173`.

## JWT

All services must share the same `jhipster.security.authentication.jwt.base64-secret`:

- `backend/src/main/resources/config/application-secret-samples.yml`
- `gateway/src/main/resources/config/application-secret-samples.yml`
- `gateway/src/main/docker/central-server-config/application.yml` (Consul central config)

## Catalog JDL

Entities are defined in `backend/miniandmore-store.jdl`. Regenerate after edits:

```bash
cd backend
jhipster jdl miniandmore-store.jdl --force
```

`CustomerOrder` and `CustomerProfile` use **`userLogin`** (string) because JWT microservices cannot use `to User with builtInEntity` when `skipUserManagement` is true.

## Catalog API (`/api/v1`)

The **miniandmore** service now includes the store API from `uat1`:

- `StorefrontResource`, `AdminStoreResource` — public and admin CRUD wrappers
- `AccountOrderResource`, `OrderReturnResource`, `ZohoSyncResource`, `StorageUploadResource`
- Zoho Books sync, Razorpay, S3 uploads, pincode lookup, order returns
- `userLogin` on orders/profiles/returns (JWT subject from gateway) instead of a local `User` table

Auth (`/api/authenticate`, register) and **social OAuth** (`/oauth2/authorization/{provider}`) run on the **gateway** only. Uncomment `spring.security.oauth2.client` in `gateway/src/main/resources/config/application-dev.yml` and set `GOOGLE_CLIENT_ID`, etc.

Legacy JHipster `/api/{entity}` endpoints are **not** exposed on the catalog; only `/api/v1/**` is allowed. See [pure-microservices.md](./pure-microservices.md).

## Module testing order

Use [MODULES_TESTING.md](./MODULES_TESTING.md) to verify each area (storefront, orders, Zoho, admin, auth) before full E2E.

Frontend path ↔ backend mapping: [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md). Quick smoke: `bash scripts/verify-frontend-api-contract.sh`.

## Production

- Run **Consul** and register gateway + miniandmore.
- Put **only the gateway** on the public URL; do not expose `8081` directly.
- Align Nginx/Jenkins with gateway JAR + catalog JAR (or Docker images).
- Use the same JWT secret in Consul config for every service.

## Useful URLs

| URL | Description |
|-----|-------------|
| http://localhost:8080 | Gateway |
| http://localhost:8081 | Catalog (dev only; use gateway in prod) |
| http://localhost:8500 | Consul UI |
| http://localhost:8080/services/miniandmore/management/health | Catalog health via gateway |
