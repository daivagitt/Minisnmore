# Module completion checklist (test in order)

Production deploy and security: [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md).

All store modules are implemented on the **catalog** (`backend/`, `miniandmore`). Auth and OAuth are on the **gateway** (`gateway/`). Use this list to verify each area before full E2E.

**Prerequisites** — see [LOCAL_DEV.md](./LOCAL_DEV.md) for the full local stack.

**No Docker?** Run **unit tests only** (no `*IT*` / Testcontainers): `bash scripts/test-local-no-docker.sh` (or `npm run test:no-docker`). Each module enforces **95% line coverage** (JaCoCo on Java services, Vitest on scoped frontend files). Integration tests (`mvn verify` Failsafe) are **not** run by default (`skipITs=true` in `backend` and `gateway`). Live module checks below still need Postgres + services.

| Module | Unit tests | Coverage gate |
|--------|------------|-----------------|
| Frontend | `npm run test:coverage` | Vitest 95% (see `vitest.config.ts` includes) |
| Gateway | `cd gateway && ./mvnw test` | JaCoCo LINE ≥ 95% |
| Catalog (`backend/`) | `cd backend && ./mvnw test` | JaCoCo LINE ≥ 95% |
| Store | `cd store && ./mvnw test` | JaCoCo LINE ≥ 95% |
| Commerce | `cd commerce && ./mvnw test` | JaCoCo LINE ≥ 95% |

```bash
docker compose -f docker/microservices.yml up -d   # optional if you have native Postgres/Redis/Consul
cd backend && ./mvnw -DskipTests
cd gateway && ./mvnw -DskipTests
npm run dev   # proxies to gateway :8080
```

Obtain JWT: `POST http://localhost:8080/api/authenticate` with admin credentials.

---

## Infrastructure

| # | Module | Status | How to verify |
|---|--------|--------|----------------|
| 1 | Consul + Postgres + Redis | Ready | `http://localhost:8500`, compose healthy |
| 2 | Gateway routes `/api/v1/**` → store / commerce | Ready | Store: `curl -s http://localhost:8080/api/v1/categories`; Ledgerly: `curl -s -H "Authorization: Bearer $JWT" http://localhost:8080/api/v1/admin/ledgerly/integration-status` |
| 3 | JWT shared secret | Ready | Login on :8080, call protected catalog route with Bearer token |

---

## Gateway (auth & users)

| # | Module | Endpoint | Notes |
|---|--------|----------|--------|
| 4 | Login | `POST /api/authenticate` | Returns `id_token` |
| 5 | Register / activate | `POST /api/register`, `GET /api/activate` | User DB on gateway |
| 6 | Account | `GET/POST /api/account` | Profile, password |
| 7 | Admin users | `/api/admin/users` | ADMIN role |
| 8 | Social OAuth | `GET /oauth2/authorization/{google\|facebook\|apple}` | Enable YAML + env in `gateway/.../application-dev.yml` |

---

## Catalog — storefront (public GET)

| # | Module | Endpoint |
|---|--------|----------|
| 9 | Categories | `GET /api/v1/categories` |
| 10 | Products | `GET /api/v1/products`, `GET /api/v1/products/{slug}` |
| 11 | Blog | `GET /api/v1/blog-posts`, `/{slug}` |
| 12 | Banners | `GET /api/v1/banners` |
| 13 | Home sections | `GET /api/v1/home-sections` |
| 14 | Page visibility | `GET /api/v1/page-visibility` |
| 15 | Site settings | `GET /api/v1/site-settings` |
| 16 | Subscribe page | `GET /api/v1/subscribe-page` |
| 17 | Policies | `GET /api/v1/policies/{slug}` |
| 18 | Pincode | `GET /api/v1/pincode/{pincode}` |

---

## Catalog — commerce (authenticated)

| # | Module | Endpoint |
|---|--------|----------|
| 19 | Coupon validate | `POST /api/v1/coupons/validate` (public) |
| 20 | Customer profile | `GET/PUT /api/v1/account/profile` |
| 21 | Orders | `POST/GET /api/v1/account/orders`, verify-payment, payments |
| 22 | Order tracking | `GET /api/v1/orders/track/{token}` (public) |
| 23 | Order returns | `POST/GET /api/v1/order-returns` |
| 24 | Schedulers | Inventory release, Ledgerly retry/tracking (logs on catalog core) |

---

## Catalog — admin

| # | Module | Endpoint |
|---|--------|----------|
| 24 | Dashboard | `GET /api/v1/admin/dashboard` |
| 25 | CRUD | categories, products, blog-posts, banners, home-sections, page-visibility, site-settings, policies, coupons |
| 26 | Ledgerly settings | `GET/PUT /api/v1/admin/ledgerly/settings` |
| 27 | Ledgerly sync | `/api/v1/admin/ledgerly/*` (test, sync, pull, retry, tracking) |
| 28 | Uploads | `/api/v1/admin/uploads/*` (S3/R2 presign + multipart) |
| 29 | Admin returns | `/api/v1/admin/order-returns` |

---

## Frontend integration

| # | Module | Verify |
|---|--------|--------|
| 30 | Vite proxy | All `/api` → :8080 |
| 31 | Store pages | Home, shop, product, blog, policies |
| 32 | Cart / checkout | Razorpay + order APIs |
| 33 | Admin | All `/admin/*` routes |
| 34 | Ledgerly admin | Settings + sync UI at `/admin/integrations/ledgerly` |

---

## UAT / CI (when deploying)

| # | Item | Script / doc |
|---|------|----------------|
| 35 | Build JARs | `backend` + `gateway` `./mvnw -Pprod -DskipTests package` |
| 36 | PM2 on VPS | `deploy/scripts/prod-microservices-pm2.sh` |
| 37 | Nginx | `deploy/nginx/minisnmore.com.locations.conf` → :8080 |
| 38 | Jenkins UAT1 | `BUILD_*` for core + gateway + store + commerce; `DEPLOY_UAT` → `/var/www/uat1/mini_and_more` |

---

## Production gates (before go-live)

| # | Check | Reference |
|---|--------|-----------|
| P1 | Env file on VPS (`prod.env`) | `deploy/env/prod.env.example` |
| P2 | PM2 + health script | `deploy/scripts/prod-microservices-pm2.sh`, `health-check-microservices.sh` |
| P3 | Nginx prod locations + security headers | `deploy/nginx/prod.minisnmore.com.locations.conf` |
| P4 | JWT + CORS + secrets not in Git | [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md) |

## Detailed Ledgerly scenarios

See [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md).

## Architecture rules

See [pure-microservices.md](./pure-microservices.md) and [microservices.md](./microservices.md).
