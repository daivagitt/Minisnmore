# System Architecture

Mini's n More runs as a **JHipster 9 / Spring Cloud microservice backend** behind a single React (TanStack Start) frontend. This document is the top-level map; see the companion docs for detail:

- [frontend-architecture.md](./frontend-architecture.md)
- [backend-architecture.md](./backend-architecture.md)
- [database-er-diagram.md](./database-er-diagram.md)
- [api-schema.md](./api-schema.md)

## Services

| Service | Folder | Stack | Port (dev) | Role |
|---|---|---|---|---|
| **Frontend** | repo root (`src/`) | TanStack Start (React 19) + Vite | 5173 | Storefront + admin CMS UI |
| **Gateway** | `gateway/` | Spring Boot 3.5 / JHipster 8.12, WebFlux | 8080 | JWT issuance, login/register/OAuth2, Spring Cloud Gateway routing, Consul |
| **Catalog core (backend)** | `backend/` (`miniandmore`) | Spring Boot 4.0 / JHipster 9.0, MVC | 8084 | Domain DB, business logic, Ledgerly sync, `/internal/v1/**` only |
| **Store** | `store/` (`miniandmore-store`) | Spring Boot 4.0 / JHipster 9.0, MVC | 8081 | Public storefront + admin CMS proxy → core |
| **Commerce** | `commerce/` (`miniandmore-commerce`) | Spring Boot 4.0 / JHipster 9.0, MVC | 8082 | Orders/profile/coupons/returns proxy → core |
| ~~Integration~~ | `integration/` | — | 8083 | **Defunct.** Deleted from git in commit `3278ca7`; its Zoho/Ledgerly responsibilities were absorbed into `backend`. Deploy scripts and `docs/PRODUCTION_READINESS.md` still reference it and need cleanup. |

Both `store` and `commerce` are **thin proxies with no database of their own** — they exist purely as auth/routing boundaries in front of `backend`, which owns all persistence and business logic.

## Request flow

```text
Browser
  → Nginx (443, public)
    → Gateway (:8080, Consul lb://)
        ├── /api/authenticate, /api/register, /api/account/*, /oauth2/**   → handled on gateway itself
        ├── /api/v1/orders/**, /account/profile, /coupons, /order-returns  → Commerce (:8082) → Backend (:8084)
        └── /api/v1/** (shop, categories, CMS, admin, ledgerly)            → Store (:8081) → Backend (:8084)
```

- Only the **gateway hop** is Consul service-discovery routed (`lb://miniandmore-store`, `lb://miniandmore-commerce`).
- The **edge → core hop** (store/commerce → backend) is a fixed `CATALOG_BASE_URL` (default `http://127.0.0.1:8084`) via Spring's synchronous `RestClient` — not Feign, not Consul.
- `backend` binds to `127.0.0.1` only in production; it is never reachable directly from the internet.
- Every proxied request carries the same Bearer JWT end-to-end; the gateway's `JWTRelayGatewayFilterFactory` decodes and relays it, and each downstream service independently validates it as an OAuth2 resource server using the same shared HMAC secret (`JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`).

## Databases

Two independent Postgres databases (see [database-er-diagram.md](./database-er-diagram.md) for full schema):

| Database | Owned by | Contents |
|---|---|---|
| `gateway` (port 5433 dev) | gateway (R2DBC, reactive) | `jhi_user`, `jhi_authority`, `jhi_user_authority` — accounts and roles |
| `miniandmore` (port 5432 dev) | backend (JPA/Hikari) | Product, Category, CustomerOrder, OrderItem, Coupon, OrderReturn, CustomerProfile, CMS content, Ledgerly settings |

Cross-database references (e.g. `CustomerOrder.userLogin` → `jhi_user.login`) are **logical only** — plain string columns, no enforced FK, since the two databases are physically separate.

Supporting infra: **Consul** (service discovery + config, port 8500), **Redis** (caching, port 6379), **S3/R2-compatible object storage** (product images/uploads).

## External integrations

- **Ledgerly** — accounting/inventory system. `backend` pushes products/orders and pulls inventory/tracking via scheduled jobs (`LedgerlySyncScheduler`, `LedgerlyInventoryPullScheduler`) and receives webhooks at `/internal/v1/ledgerly/webhooks`.
- **Razorpay** — payment gateway, integrated directly in `backend` (`RazorpayRefundService`, order verification flow).
- **Google OAuth2** — social login, terminates on the gateway.

## Deployment topology (production/UAT)

```text
Nginx (443, public)
  → Gateway   127.0.0.1:8080
      → Store     127.0.0.1:8081
      → Commerce  127.0.0.1:8082
          → Backend (catalog core) 127.0.0.1:8084
Consul  127.0.0.1:8500
Postgres (catalog) 127.0.0.1:5432
Postgres (gateway)  127.0.0.1:5433
Redis   127.0.0.1:6379
```

All Java services run under **PM2** (`deploy/scripts/prod-microservices-pm2.sh` / `uat-microservices-pm2.sh`), built via `./mvnw -Pprod -DskipTests clean package` per module and health-checked against `/management/health/{liveness,readiness}`. The frontend is built with Vite and served separately (see `uat-frontend-pm2.sh`). See `docs/PRODUCTION_READINESS.md` and `docs/LOCAL_DEV.md` for the full runbook (note: those docs still list the defunct `integration` service — treat that as stale).

## Known architectural discrepancies (worth fixing)

- `integration/` is deleted from source control but still referenced by `docs/PRODUCTION_READINESS.md` and several `deploy/scripts/*.sh` (build/health-check loops iterate over it).
- The Ledgerly webhook endpoint (`/internal/v1/ledgerly/webhooks`) is not proxied by any committed Nginx config or gateway route — only reachable if something outside the repo (e.g. a manual VPS Nginx block) forwards it.
