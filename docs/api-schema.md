# API Schema

REST API surface across all four services. See [system-architecture.md](./system-architecture.md) for the request-flow diagram and [backend-architecture.md](./backend-architecture.md) for service internals.

```text
Browser → Nginx → Gateway (:8080) → [Store :8081 | Commerce :8082] → Backend "catalog core" (:8084, internal only)
```

## Versioning & path scheme

| Prefix | Owner | Meaning |
|---|---|---|
| `/api/*` (no version) | gateway | JHipster auth/account/user-admin boilerplate |
| `/api/v1/**` | store / commerce (proxy) → backend | The one public, versioned catalog/commerce API — reached only through the gateway in production |
| `/internal/v1/**` | backend | Business logic, meant to be reached only via store/commerce; **not proxied by any committed Nginx config or gateway route** — not internet-reachable through anything in this repo |
| `/api/v1/admin/ledgerly/**` | backend directly | Sole exception living under `/api/v1` on backend itself, routed straight through by store |

There is no single current OpenAPI spec covering the full proxied surface unless **catalog-core** runs with the `api-docs` profile (`/v3/api-docs` on `:9184`, proxied by UAT nginx). springdoc scans `backend` controllers under `/internal/v1/**` and `/api/v1/**`, remapping internal paths to the public `/api/v1/**` contract. Store/commerce proxies are 1:1 with that surface. Gateway auth (`/api/authenticate`, `/api/account`, …) is documented separately when the gateway runs with `api-docs`. Regenerate snapshots with `deploy/scripts/export-openapi.sh`. `openapi-all.zip` at the repo root is Zoho Books' *own* API spec, not this app's. Treat this document plus [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md) as the semantic source of truth for payloads and behaviour.

## Auth scheme

Bearer JWT (`Authorization: Bearer <token>`), issued only by gateway (`POST /api/authenticate`, or Google OAuth2 via `/oauth2/authorization/google`). Every downstream service independently validates the same token as an OAuth2 resource server (shared HMAC secret). See [backend-architecture.md](./backend-architecture.md#authentication--authorization).

## Response envelope

Backend's `ApiResponse<T>` (`backend/.../web/rest/v1/ApiResponse.java`):
```json
{ "success": true, "data": {}, "meta": { "requestId": "...", "timestamp": "..." } }
```
Note: catalog/CMS DTOs (`ProductDTO`, `CategoryDTO`, etc.) use **camelCase**, but the hand-built order/account/return endpoints (`InternalAccountOrderResource`, `InternalOrderReturnResource`) use **snake_case** JSON keys (`full_name`, `order_number`, `product_id`) — an intentional inconsistency documented in [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md). Field-level order/return schemas: [order-return-json-contract.md](./order-return-json-contract.md).

---

## Gateway (`/api`) — port 8080

| Path | Method | Purpose | Auth |
|---|---|---|---|
| `/authenticate` | POST | Login → `{ id_token }` | permitAll |
| `/authenticate` | GET | Check current auth | authenticated |
| `/register` | POST | Self-registration | permitAll |
| `/activate?key=` | GET | Activate account | permitAll |
| `/account` | GET/POST | Current user info / update | authenticated |
| `/account/change-password` | POST | Change password | authenticated |
| `/account/resend-activation` | POST | Resend activation email | permitAll |
| `/account/reset-password/init` | POST | Request reset email | permitAll |
| `/account/reset-password/finish` | POST | Complete reset | permitAll |
| `/users` | GET | Public user directory (paged) | permitAll |
| `/admin/users` | GET/POST/PUT/DELETE | User CRUD | ROLE_ADMIN |
| `/authorities` | GET/POST/DELETE | Authority CRUD | ROLE_ADMIN |
| `/gateway/routes` | GET | List active Spring Cloud Gateway routes | ROLE_ADMIN |
| `/oauth2/**`, `/login/oauth2/**` | — | Google OAuth2 login flow | permitAll |

---

## Catalog / storefront — `/api/v1` (public reads, via store proxy)

Backed by `backend`'s `InternalStorefrontResource` (`/internal/v1/**`), all GET, `permitAll`:

| Path | Purpose |
|---|---|
| `/categories` | Active categories, sorted |
| `/products` | Active products with category |
| `/products/{slug}` | Single active product |
| `/blog-posts`, `/blog-posts/{slug}` | Published blog posts |
| `/banners` | Active banners |
| `/home-sections` | Enabled home sections |
| `/page-visibility` | Page visibility flags |
| `/site-settings` | Brand/GST/shipping settings |
| `/subscribe-page` | Subscribe-page CMS fields |
| `/pincode/{pincode}` | Pincode serviceability lookup |
| `/policies/{slug}` | Policy page by slug |

## Admin CMS — `/api/v1/admin` (via store proxy, ROLE_ADMIN)

Backed by `InternalAdminStoreResource`:

| Domain | Paths |
|---|---|
| Dashboard | GET `/dashboard` |
| Categories | GET/POST `/categories`, PUT/DELETE `/categories/{id}` |
| Products | GET/POST `/products`, PUT/DELETE `/products/{id}` (save triggers async Ledgerly sync) |
| Blog | GET/POST `/blog-posts`, PUT/DELETE `/blog-posts/{id}` |
| Banners | GET/POST `/banners`, PUT/DELETE `/banners/{id}` |
| Home sections | GET `/home-sections`, PUT `/home-sections/{id}` |
| Page visibility | GET `/page-visibility`, PUT `/page-visibility/{id}` |
| Site settings | GET/PUT `/site-settings` |
| Policies | GET/POST `/policies`, PUT/DELETE `/policies/{id}` |
| Coupons | GET/POST `/coupons`, PUT/DELETE `/coupons/{id}` |

### Import & uploads (via store proxy, ROLE_ADMIN)

| Path | Method | Purpose |
|---|---|---|
| `/api/v1/admin/import/sku-wise-print` | POST (multipart) | SKU-wise print import — shells out to `deploy/scripts/uat-sku-wise-print-import.py` |
| `/api/v1/admin/uploads/presign` | POST | S3/R2 presigned upload URL |
| `/api/v1/admin/uploads` | POST (multipart) | Direct upload to S3/R2 |
| `/api/v1/admin/uploads/objects` | GET | List stored objects |
| `/api/v1/admin/uploads/delete` | POST | Bulk-delete objects |

> `InternalStorageUploadResource` (backing the uploads endpoints) has **no `@PreAuthorize`** unlike its sibling admin resources — access control relies entirely on store's `/api/v1/admin/**` → ROLE_ADMIN rule, not self-enforcement.

## Ledgerly admin — `/api/v1/admin/ledgerly` (backend directly, via store)

Backed by `LedgerlyAdminResource`, protected by backend's filter-chain rule for `/api/v1/admin/**` (ROLE_ADMIN):

| Path | Method | Purpose |
|---|---|---|
| `/integration-status` | GET | Connection/config status |
| `/test` | GET | Test connection to Ledgerly |
| `/settings` | GET/PUT | API base URL, org ID, token, webhook secret |
| `/pull-products-auto-create-category` | PUT | Toggle auto-create-category |
| `/sync-products` | POST | Push all products to Ledgerly |
| `/sync-products/{id}` | POST | Push single product |
| `/pull-products` | POST | Pull products from Ledgerly |
| `/pull-inventory` | POST | Pull inventory from Ledgerly |
| `/retry-orders` | POST | Retry failed order syncs |
| `/sync-orders/by-number/{orderNumber}` | POST | Sync order by order number |
| `/pull-order-tracking` | POST | Pull tracking updates for all open orders |

### Ledgerly webhook (inbound)

`InternalLedgerlyWebhookResource`, `POST /internal/v1/ledgerly/webhooks` — headers `X-Ledgerly-Signature`, `X-Ledgerly-Event`. **Reachability gap**: documented as `https://uat.minisnmore.com/internal/v1/ledgerly/webhooks` but no committed Nginx config or gateway route proxies `/internal/v1/*` — likely relies on an out-of-repo Nginx block. Needs verification/fix.

---

## Orders, coupons, account, returns — `/api/v1` (via commerce proxy)

`CommerceOrdersProxyResource`, `OrderReturnsProxyResource`, `AdminOrderReturnsProxyResource` proxy 1:1 to backend's `InternalAccountOrderResource` / `InternalOrderReturnResource` / `InternalAdminOrderReturnResource`.

| Path | Method | Purpose | Auth |
|---|---|---|---|
| `/coupons/validate` | POST | Validate coupon against subtotal | permitAll |
| `/account/profile` | GET/PUT | Customer profile (name, phone) | authenticated |
| `/orders` | POST | Create order (reserves inventory, computes totals, creates Razorpay order) | authenticated |
| `/orders/verify-payment` | POST | Verify Razorpay signature, confirm order, trigger Ledgerly sync | authenticated |
| `/orders/sync-payment` | POST | Poll Razorpay for async/UPI confirmation | authenticated |
| `/orders` | GET | List my orders | authenticated |
| `/orders/track/{orderNumber}` | GET | Public order tracking (limited fields) | permitAll |
| `/orders/{orderNumber}` | GET | Order detail (ownership enforced) | authenticated |
| `/payments` | GET | My payment history | authenticated |
| `/order-returns` | GET | List my returns | authenticated |
| `/order-returns` | POST | Request a return (`order_number`, `reason`) | authenticated |
| `/order-returns/{returnNumber}/cancel` | POST | Cancel my return | authenticated |
| `/order-returns/eligibility/{orderNumber}` | GET | Check return eligibility | authenticated |
| `/admin/order-returns` | GET (`?status=`) | List all returns (admin) | ROLE_ADMIN |
| `/admin/order-returns/{returnNumber}/approve` | POST | Approve return | ROLE_ADMIN |
| `/admin/order-returns/{returnNumber}/reject` | POST | Reject return (`rejection_reason`) | ROLE_ADMIN |
| `/admin/order-returns/{returnNumber}/mark-received` | POST | Mark item received | ROLE_ADMIN |
| `/admin/order-returns/{returnNumber}/refund` | POST | Process refund (Razorpay + Ledgerly credit note) | ROLE_ADMIN |

There is **no server-side cart API** — the cart is client-only ([frontend-architecture.md](./frontend-architecture.md#state-management)); checkout submits the full item list directly to `POST /orders`.

---

## Domain → owning service quick reference

| Domain | Owner(s) | Path prefix |
|---|---|---|
| Auth | gateway | `/api/authenticate`, `/api/register`, `/api/account/*`, `/oauth2/**` |
| User/authority admin | gateway | `/api/admin/users`, `/api/authorities` |
| Products / categories (read) | backend via store | `/api/v1/products`, `/api/v1/categories` |
| Products / categories (admin) | backend via store | `/api/v1/admin/products`, `/api/v1/admin/categories` |
| Orders / checkout / payments | backend (logic), commerce (proxy) | `/api/v1/orders*`, `/api/v1/payments` |
| Coupons | backend, commerce | `/api/v1/coupons/validate`, `/api/v1/admin/coupons` |
| Returns | backend, commerce | `/api/v1/order-returns*`, `/api/v1/admin/order-returns*` |
| Account/profile | backend, commerce | `/api/v1/account/profile` |
| CMS (banners/blog/home/pages/policies/settings) | backend via store | `/api/v1/admin/{banners,blog-posts,home-sections,page-visibility,policies,site-settings}` (+ public read equivalents) |
| Ledgerly integration | backend | `/api/v1/admin/ledgerly/*`, `/internal/v1/ledgerly/webhooks` |
| Storage/uploads | backend via store | `/api/v1/admin/uploads*` |
| SKU-wise print import | backend via store | `/api/v1/admin/import/sku-wise-print` |
| Pincode lookup | backend | `/api/v1/pincode/{pincode}` |
| Gateway routing introspection | gateway | `/api/gateway/routes` |

## Related docs

- [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md) — existing frontend-path ↔ backend-path mapping
- [backend-architecture.md](./backend-architecture.md)
- [system-architecture.md](./system-architecture.md)
- [database-er-diagram.md](./database-er-diagram.md)
