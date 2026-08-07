# Backend Architecture

Four Spring Boot / JHipster services. `backend` (catalog core) owns all business logic and the primary database; `gateway` owns auth; `store` and `commerce` are stateless proxy/edge services with no database of their own. See [system-architecture.md](./system-architecture.md) for the request-flow diagram and [api-schema.md](./api-schema.md) for endpoints.

## Service stack matrix

| Service | Spring Boot | JHipster | Spring Cloud | Web stack | Notes |
|---|---|---|---|---|---|
| `gateway/` | 3.5.11 | 8.12.0 | 2025.0.0 | WebFlux (reactive) | Not yet upgraded — version skew vs. the other three |
| `backend/` | 4.0.3 | 9.0.0 | 2025.1.1 | MVC (servlet) | Catalog core, owns `miniandmore` DB |
| `store/` | 4.0.3 | 9.0.0 | 2025.1.1 | MVC (servlet) | No datasource — pure proxy |
| `commerce/` | 4.0.3 | 9.0.0 | 2025.1.1 | MVC (servlet) | No datasource — pure proxy |

Java 21 across all services.

## gateway/

`com.miniandmore.gateway`

- **Dependencies**: `spring-cloud-starter-gateway-server-webflux`, `spring-cloud-starter-consul-discovery` + `-consul-config`, `spring-cloud-starter-circuitbreaker-reactor-resilience4j`, `spring-boot-starter-oauth2-client` + `-oauth2-resource-server`, `spring-boot-starter-data-r2dbc` (reactive, owns its own user DB).
- **`web/rest/`** — `AuthenticateController` (`POST/GET /authenticate`, JWT issuance), `AccountResource` (`/register`, `/activate`, `/account/*`, password reset), `UserResource`/`AuthorityResource` (`/api/admin/**`, ROLE_ADMIN user management), `PublicUserResource`, `GatewayResource` (`GET /api/gateway/routes`, introspection).
- **`security/`** — `JwtTokenService` (token minting), `DomainUserDetailsService`, Google OAuth2 social login (`OAuth2AuthenticationSuccessHandler/FailureHandler`, `OAuth2UserTokenService`, cookie-based authorization request repo), and `security/jwt/JWTRelayGatewayFilterFactory` — the custom Gateway filter that decodes the inbound bearer token per-request and relays it downstream (or passes anonymous requests through unchanged).
- **`config/`** — `SecurityConfiguration`, `GatewayProductionValidator`, `DatabaseConfiguration` (own R2DBC DB, port 5433 dev), `ReactorConfiguration`, `PasswordEncoderConfiguration`.
- **`domain/` / `repository/`** — gateway-local `User`/`Authority` R2DBC entities (see [database-er-diagram.md](./database-er-diagram.md)).
- **Routing** (`application.yml`, `spring.cloud.gateway.server.webflux.routes`): three routes, all `lb://` (Consul load-balanced):
  - `miniandmore-store-api` — `Path=/api/v1/**` → `lb://miniandmore-store`
  - `miniandmore-commerce-api` — orders/account/payments/coupons/order-returns paths → `lb://miniandmore-commerce`
  - `miniandmore-ledgerly-api` — `/api/v1/admin/ledgerly/**` → `lb://miniandmore-store` (passed straight through to backend)
  - `default-filters: [JWTRelay]` applies to every route. Discovery-locator fallback also enabled for ad-hoc `/services/{serviceId}/**` routing.
  - Timeouts: 5s connect / 120s response. Graceful shutdown, 30s phase timeout.

## backend/ (catalog core, `com.miniandmore.app`)

The only service with a real domain model and business logic.

- **Dependencies**: `spring-data-jpa` + `spring-boot-starter-liquibase`, `spring-boot-starter-web` (MVC) + `-security` + `-oauth2-resource-server`, `spring-cloud-starter-consul-discovery/-config`, `software.amazon.awssdk:s3` (S3/R2 object storage), `org.redisson:redisson` (Redis cache), MapStruct, Hibernate 7, springdoc-openapi.
- **`service/`** — domain services paired with `XxxQueryService` (JPA-Specification/Criteria search): `ProductService`, `CategoryService`, `CustomerOrderService`, `CouponService`, `BannerService`, `BlogPostService`, `HomeSectionService`, `PageVisibilityService`, `PolicyService`, `SiteSettingService`, `CustomerProfileService`, `OrderItemService`, `OrderReturnService`. Utility/support: `S3StorageService`, `RazorpayRefundService`, `RazorpayErrorMapper`, `PincodeLookupService`, `ProductVariantSupport`, `ProductColorImagesSupport`, `ProductDescriptionCodec`, `OrderTrackingStatusResolver`.
- **Ledgerly cluster** — `LedgerlySyncService`, `LedgerlySyncScheduler`, `LedgerlyInventoryPullScheduler`, `LedgerlyWebhookHandlerService`, `LedgerlyCategoryResolver`, `LedgerlyCredentialsProvider`, `LedgerlyItemImageSync`, `LedgerlyProductMergeService`, `LedgerlyOrderStatus`/`LedgerlySyncSummary`; outbound HTTP client in `client/LedgerlyIntegrationClient`.
- **`web/rest/`** split by intent:
  - `v1/` — shared helper types only (`ApiResponse` envelope). **No public controllers live here** — confirms backend never exposes `/api/v1` itself except the one Ledgerly-admin exception below.
  - `internal/` — the real surface: `InternalStorefrontResource`, `InternalAccountOrderResource`, `InternalAdminStoreResource`, `InternalAdminImportResource`, `InternalAdminOrderReturnResource`, `InternalOrderReturnResource`, `InternalStorageUploadResource`, `InternalLedgerlyWebhookResource` — all under `/internal/v1/**`.
  - `admin/` — `LedgerlyAdminResource`, the one controller that lives under `/api/v1/admin/ledgerly/**` directly (routed through unchanged by `store`).
- **`security/`** — `AuthoritiesConstants` (`ROLE_ADMIN`/`ROLE_USER`/`ROLE_ANONYMOUS`), `SecurityUtils`, `SpringSecurityAuditorAware`.
- **`config/`** — `SecurityConfiguration`, `SecurityJwtConfiguration` (HMAC `NimbusJwtDecoder`), `ApplicationProperties` (typed `application.*` binding: storage/ledgerly/inventory/razorpay), `DatabaseConfiguration`, `LiquibaseConfiguration`, `CacheConfiguration`/`CacheKeyGeneratorConfiguration` (Redis via Redisson), `CatalogProductionValidator` (fails fast on missing prod secrets).
- **`management/SecurityMetersService`** — Micrometer counters for JWT decode failures (invalid signature/expired/malformed).
- **Config keys** (`application.yml` / `application-{dev,prod}.yml`): `application.internal-api-key` (shared secret for edge→core calls), `application.ledgerly.*` (api-base-url, api-token, org-id, webhook-secret, sync flags, scheduler intervals), `application.inventory.reservation-release.fixed-delay-ms`, `razorpay.{key-id,key-secret}`, `application.storage.s3.*`. Prod binds `server.address: 127.0.0.1` — never public.

### Scheduled jobs (backend)

| Job | Class | Interval (env) | Purpose |
|---|---|---|---|
| Retry failed order sync | `LedgerlySyncScheduler.retryFailedOrderSync()` | `LEDGERLY_ORDER_RETRY_MS` (default 5min) | Re-attempts `PENDING`/`FAILED` order syncs to Ledgerly, per-order try/catch |
| Pull order tracking | `LedgerlySyncScheduler.pullOpenOrderTracking()` | `LEDGERLY_ORDER_TRACKING_MS` (default 1hr) | Pulls shipment/tracking status back from Ledgerly for open orders |
| Pull inventory | `LedgerlyInventoryPullScheduler.pullInventory()` | `LEDGERLY_INVENTORY_PULL_MS` | Syncs stock levels down from Ledgerly |
| Release inventory reservations | (in `InternalAccountOrderResource`) | `application.inventory.reservation-release.fixed-delay-ms` | Releases expired checkout inventory holds |

All Ledgerly schedulers are individually gated by `@ConditionalOnProperty` (`auto-sync-enabled`, `auto-pull-enabled`) and disabled by default in dev unless `LEDGERLY_API_TOKEN` is set.

## store/ (`com.miniandmore.store`) — proxy only

- **Dependencies**: `spring-boot-starter-web/-security/-oauth2-resource-server/-validation`, `spring-cloud-starter-consul-discovery` only — **no JPA, no Liquibase, no datasource**.
- **`client/CatalogCoreClient`** — forwards generic requests, rewriting `/api/v1/X` → `/internal/v1/X` on backend (Ledgerly admin paths pass through unchanged). Built on `RestClient` (`config/RestClientConfiguration`), not Feign, with a fixed `catalog-base-url` (env `CATALOG_BASE_URL`, default `http://127.0.0.1:8084`) — **not** Consul-discovered.
- **`web/`** — `StoreCatalogProxyResource` (generic `/api/v1/**` catch-all proxy), `AdminImportProxyResource`, `AdminUploadProxyResource` (multipart re-encoding, since the catch-all can't stream raw multipart bytes).

## commerce/ (`com.miniandmore.commerce`) — proxy only

- Same shape as `store`: no datasource, `RestClientConfiguration` + `CatalogInternalClient`.
- **`web/`** — `CommerceOrdersProxyResource` (coupons/profile/orders/payments), `OrderReturnsProxyResource`, `AdminOrderReturnsProxyResource` — all 1:1 proxies to backend's `/internal/v1/**` equivalents.
- Has the **strictest** security config of the four services (explicit `anyRequest().denyAll()` catch-all).

## Inter-service communication

Two distinct mechanisms at two hops:

1. **Gateway → store/commerce**: Spring Cloud Gateway + Consul + `lb://` load-balancing, JWT relayed via `JWTRelayGatewayFilterFactory`.
2. **Store/commerce → backend**: direct synchronous `RestClient` calls to a fixed, env-configured URL — no service discovery at this hop.

```text
Nginx → Gateway (Consul lb://) → Store/Commerce (fixed RestClient URL) → Backend (127.0.0.1:8084, internal-only)
```

Backend's only outbound call is to the external Ledgerly API via `client/LedgerlyIntegrationClient`.

## Authentication & authorization

- **Issuance**: gateway only (`AuthenticateController` + `JwtTokenService`), against gateway's local user DB; OAuth2 social login also terminates here and produces the same JWT format.
- **Relay**: `JWTRelayGatewayFilterFactory` decodes and forwards the bearer token unchanged on every proxied gateway route; anonymous requests pass through.
- **Validation**: every downstream service independently runs `SecurityJwtConfiguration` → `NimbusJwtDecoder` (HMAC, shared secret `JHIPSTER_SECURITY_AUTHENTICATION_JWT_BASE64_SECRET`, `authorities-claim-name: auth`).
- **Authorization** (backend `SecurityConfiguration`): stateless, CSRF disabled; `/internal/v1/**` `permitAll` at the filter-chain level (safe only because backend is loopback-only in prod — actual per-endpoint checks happen in code via `SecurityUtils.getCurrentUserLogin()` or class-level `@PreAuthorize("hasAuthority('ROLE_ADMIN')")`); `/api/v1/admin/**` → `ROLE_ADMIN`; `/management/**` (except health/info) → `ROLE_ADMIN`; everything else → `authenticated()`.
- **Roles**: `ROLE_ADMIN`, `ROLE_USER`, `ROLE_ANONYMOUS` — identical `AuthoritiesConstants` in all four services.
- **Internal secret**: `application.internal-api-key` (env `INTERNAL_API_KEY`) — a separate shared secret layered on top of JWT for edge→core calls.

## Deployment topology

```text
Nginx (443, public)
  → Gateway   127.0.0.1:8080
      → Store     127.0.0.1:8081
      → Commerce  127.0.0.1:8082
          → Backend 127.0.0.1:8084 (internal only)
Consul  127.0.0.1:8500
Postgres (catalog) 127.0.0.1:5432   Postgres (gateway) 127.0.0.1:5433
Redis   127.0.0.1:6379
```

- Local dev infra: `docker compose -f docker/microservices.yml up -d` — Consul (dev mode), 2× Postgres 18.3, Redis 8.6.1.
- Production/UAT: each service built via `./mvnw -Pprod -DskipTests clean package`, run under **PM2** (`deploy/scripts/prod-microservices-pm2.sh` / `uat-microservices-pm2.sh`), health-checked via `/management/health/{liveness,readiness}` (`deploy/scripts/health-check-microservices.sh`). Secrets in `/etc/mini-and-more/prod.env` (chmod 600). Nginx config: `deploy/nginx/prod.minisnmore.com.locations.conf`.
- Profile matrix: local dev = `dev`; UAT = gateway `prod,api-docs,uat`, others `prod,api-docs`; production = `prod` (+`consul` if using the config server).
- CI: Jenkins jobs `BUILD_GATEWAY`, `BUILD_BACKEND`, `BUILD_STORE`, `BUILD_COMMERCE`, `BUILD_INTEGRATION` (defunct, see below), `DEPLOY_UAT`.

### `integration/` is defunct

Deleted from git in commit `3278ca7` ("Implement Ledgerly integration... Replace ZohoBooksIntegrationClient references with Ledgerly counterparts") — no `pom.xml`, no tracked source. Its former responsibility (Zoho/Ledgerly settings + webhooks) now lives entirely in `backend` (`LedgerlySetting`, `LedgerlyWebhookEvent`, `Ledgerly*` services). What remains on disk under `integration/` is stale build artifacts, not live code. **However**, `docs/PRODUCTION_READINESS.md` and several `deploy/scripts/*.sh` still iterate over `integration` as a fifth service to build/deploy/health-check — this should be cleaned up or the scripts will no-op/fail on that step.

## Related docs

- [system-architecture.md](./system-architecture.md)
- [api-schema.md](./api-schema.md)
- [database-er-diagram.md](./database-er-diagram.md)
- [pure-microservices.md](./pure-microservices.md) — hard architectural boundary rules
- [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md), [LOCAL_DEV.md](./LOCAL_DEV.md)
