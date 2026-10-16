# Pure microservice rules

This repository follows a **gateway + catalog** split. Treat these as hard boundaries.

## Service roles

| Service | Folder | Port (dev) | Owns |
|---------|--------|------------|------|
| **Gateway** | `services/gateway/` | 8080 | Users, JWT **issuance**, `/api/authenticate`, `/api/register`, `/api/account/*`, Spring Cloud Gateway |
| **Catalog core** | `backend/` (`miniandmore`) | 8084 | DB, domain logic, Ledgerly sync/schedulers, **`/internal/v1/**` only** (not public) |
| **Store** | `services/store/` (`miniandmore-store`) | 8081 | Public storefront + admin CMS + uploads; proxies to core (including Ledgerly admin API) |
| **Commerce** | `services/commerce/` (`miniandmore-commerce`) | 8082 | Orders, profile, coupons, returns; proxies to core |

Ledgerly accounting integration runs **inside catalog core** — there is no separate integration microservice on `:8083`.

## Catalog (`miniandmore`) must NOT

- Expose `/api/categories`, `/api/products`, or other JHipster `/api/{entity}` CRUD (use `/api/v1` via `*Resource` under `web/rest/v1/`).
- Issue JWTs (`JwtEncoder` is not registered; tokens come from the gateway).
- Host Spring Security user tables or `/api/authenticate` ( `skipUserManagement: true` ).
- Hold Google/Facebook/Apple OAuth client config (gateway: `spring.security.oauth2.client` + `application.oauth2.frontend-callback-url`).

## Gateway must

- Be the **only public HTTP API** in production (catalog `:8081` is internal).
- Route **`/api/v1/**`** to `lb://miniandmore` via Consul.
- Share the same `jhipster.security.authentication.jwt.base64-secret` as the catalog.
- Apply the same public `/api/v1` `permitAll` rules as the catalog so unauthenticated storefront GETs work before proxying.

## Request flow

```text
Browser → Vite (:5173) → Gateway (:8080)
  ├── /api/authenticate, /api/register, /api/account/*     → gateway app
  ├── /api/v1/admin/ledgerly/**                            → store (:8081) → core (:8084)
  ├── /api/v1/orders/**, account, coupons, returns         → commerce (:8082)
  └── /api/v1/** (shop + admin CMS)                        → store (:8081) → core (:8084)
```

## Ledgerly env vars (catalog core)

| Variable | Purpose |
|----------|---------|
| `LEDGERLY_API_BASE_URL` | Ledgerly API base URL |
| `LEDGERLY_API_TOKEN` | Developer token (`lt_live_…`) |
| `LEDGERLY_ORG_ID` | Organisation ID |
| `LEDGERLY_ORDER_RETRY_MS` | Failed-order retry scheduler (optional) |
| `LEDGERLY_ORDER_TRACKING_MS` | Tracking pull scheduler (optional) |

## Regenerating JHipster entities

After `jhipster jdl`, JHipster may recreate `*Resource.java` under `/api/{entity}`. Either delete those controllers again or keep catalog security as `denyAll` for `/api/**` outside `/api/v1/**`.

## Infrastructure

Use `docker/microservices.yml` (Consul, Postgres ×2, Redis). Do not enable per-app Spring Docker Compose when using the shared stack.

## Splitting the catalog further

The catalog is split into **store + commerce + core**. See [catalog-microservice-split.md](./catalog-microservice-split.md) and `backend/jdl/*.jdl`.

## Production

See [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md) for env files, PM2 scripts, Nginx hardening, and the go-live checklist.
