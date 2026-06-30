# Microservices architecture (JHipster 9)

This branch runs a **JHipster microservice stack** for Minis & More:

| App | Folder | Port | Role |
|-----|--------|------|------|
| **Gateway** | `gateway/` | **8080** | JWT login/register, Spring Cloud Gateway, Consul |
| **Catalog core** | `backend/` (`miniandmore`) | **8084** | Domain DB, Ledgerly sync, internal APIs |
| **Store** | `store/` (`miniandmore-store`) | **8081** | Storefront + admin CMS; proxies to core |
| **Commerce** | `commerce/` (`miniandmore-commerce`) | **8082** | Orders, profile, coupons, returns |

JHipster puts **users and authentication on the gateway**. The catalog service uses `skipUserManagement` and stores `userLogin` on orders/profiles.

## Request flow

```text
Browser (Vite :5173)
  → proxy /api → Gateway :8080
       → /api/authenticate, /api/register, /api/account/*  (handled on gateway)
       → /api/v1/admin/ledgerly/**                          → store :8081 → core :8084
       → /api/v1/orders/**, account, coupons, returns       → commerce :8082
       → /api/v1/** (shop + admin CMS)                      → store :8081 → core :8084
```

## Local development

See [LOCAL_DEV.md](./LOCAL_DEV.md) for Docker, `dev-start-microservices.sh`, and smoke checks.

## JWT

All services share `jhipster.security.authentication.jwt.base64-secret` (gateway + catalog core + edge services).

## Catalog API (`/api/v1`)

- `StorefrontResource`, `AdminStoreResource` — public and admin CRUD
- `AccountOrderResource`, `OrderReturnResource`, `LedgerlyAdminResource`, `StorageUploadResource`
- Ledgerly sync, Razorpay, S3 uploads, pincode lookup, order returns

Auth and social OAuth run on the **gateway** only.

## Module testing order

Use [MODULES_TESTING.md](./MODULES_TESTING.md). Ledgerly scenarios: [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md).

Frontend path ↔ backend mapping: [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md).

## Architecture rules

See [pure-microservices.md](./pure-microservices.md).

## Useful URLs

| URL | Description |
|-----|-------------|
| http://localhost:8080 | Gateway |
| http://localhost:8081 | Store (dev only; use gateway in prod) |
| http://localhost:8500 | Consul UI |
