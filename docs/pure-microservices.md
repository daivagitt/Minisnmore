# Pure microservice rules

This repository follows a **gateway + catalog** split. Treat these as hard boundaries.

## Service roles

| Service | Folder | Port (dev) | Owns |
|---------|--------|------------|------|
| **Gateway** | `gateway/` | 8080 | Users, JWT **issuance**, `/api/authenticate`, `/api/register`, `/api/account/*`, Spring Cloud Gateway |
| **Catalog core** | `backend/` (`miniandmore`) | 8084 | DB, domain logic, **`/internal/v1/**` only** (not public) |
| **Store** | `store/` (`miniandmore-store`) | 8081 | Public storefront + admin CMS + uploads; proxies to core |
| **Commerce** | `commerce/` (`miniandmore-commerce`) | 8082 | Orders, profile, coupons, returns; proxies to core |
| **Integration** | `integration/` (`miniandmore-integration`) | 8083 | Zoho admin API; proxies to core |

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
  ├── /api/authenticate, /api/register, /api/account/*  → gateway app
  ├── /api/v1/admin/zoho/**                           → integration (:8083)
  ├── /api/v1/orders/**, account, coupons, returns    → commerce (:8082)
  └── /api/v1/** (shop + admin CMS)                   → store (:8081) → core (:8084)
```

## Regenerating JHipster entities

After `jhipster jdl`, JHipster may recreate `*Resource.java` under `/api/{entity}`. Either delete those controllers again or keep catalog security as `denyAll` for `/api/**` outside `/api/v1/**`.

## Infrastructure

Use `docker/microservices.yml` (Consul, Postgres ×2, Redis). Do not enable per-app Spring Docker Compose when using the shared stack.

## Splitting the catalog further

The catalog is **one** microservice today (`miniandmore`). To split it into store + commerce + integration, see [catalog-microservice-split.md](./catalog-microservice-split.md) and `backend/jdl/*.jdl`.

## Production

See [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md) for env files, PM2 scripts, Nginx hardening, and the go-live checklist.
