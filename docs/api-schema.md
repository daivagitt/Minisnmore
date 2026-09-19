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
| `/api/v1/admin/ledgerly/**`, `/api/v1/admin/zoho/**`, `/api/v1/admin/zoho-settings/**` | backend directly | Integration admin surfaces living under `/api/v1` on backend itself, routed straight through by store |

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
| Products | GET/POST `/products`, PUT/DELETE `/products/{id}`, POST `/products/{id}/clear-variant-photos` (save triggers async Ledgerly + Zoho sync) |
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
| `/api/v1/admin/import/sku-wise-print` | POST (multipart) | SKU-wise print import — shells out to `deploy/scripts/prod-sku-wise-print-import.py` |
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

`InternalLedgerlyWebhookResource`, `POST /internal/v1/ledgerly/webhooks` — headers `X-Ledgerly-Signature`, `X-Ledgerly-Event`. **Reachability gap**: documented as `https://minisnmore.com/internal/v1/ledgerly/webhooks` but no committed Nginx config or gateway route proxies `/internal/v1/*` — likely relies on an out-of-repo Nginx block. Needs verification/fix.

## Zoho Books admin — `/api/v1/admin/zoho` (backend directly, via store)

Backed by `ZohoAdminResource`, protected by backend's filter-chain rule for `/api/v1/admin/**` (ROLE_ADMIN). Mirrors the Ledgerly admin surface for Zoho Books:

| Path | Method | Purpose |
|---|---|---|
| `/oauth-setup` | GET | OAuth console URLs, scopes, redirect URIs |
| `/integration-status` | GET | Connection/config status |
| `/connect-url` | GET | OAuth authorization URL |
| `/oauth/complete` | POST | Exchange authorization code for tokens |
| `/test` | GET | Test connection to Zoho Books |
| `/pull-products-auto-create-category` | PUT | Toggle auto-create-category on product pull |
| `/sync-products` | POST | Push all products to Zoho |
| `/sync-products/{id}` | POST | Push single product |
| `/sync-stock/{id}` | POST | Sync stock for one product |
| `/pull-products` | POST (`?full=`) | Pull products from Zoho |
| `/pull-products/{id}` | POST | Pull single product by catalog id |
| `/pull-products/{id}/variants` | POST | Pull one variant (`sku`, `zohoItemId`) |
| `/pull-product` | POST | Pull external product by SKU / Zoho item id |
| `/pull-inventory` | POST | Pull inventory from Zoho |
| `/retry-orders` | POST | Retry failed order syncs |
| `/sync-orders/by-number/{orderNumber}` | POST | Sync order by order number |
| `/pull-order-tracking` | POST | Pull tracking updates for all open orders |

Settings (non-secret fields only) live at `/api/v1/admin/zoho-settings` (`ZohoSettingsResource`, GET/PUT).

### Zoho inbound webhooks

All handlers live under `InternalZoho*WebhookResource` with dual mapping `/internal/v1/zoho/*` and `/api/v1/zoho/*`. Gateway and Nginx proxy the public paths on UAT. Auth is HMAC-SHA256 (`X-Zoho-*-Signature`) or shared secret header (`X-Zoho-*-Secret`). Each handler persists an idempotency row keyed by `eventId`.

| Webhook | Resource | Public path | Auth header(s) | Handler |
|---|---|---|---|---|
| Stock | `InternalZohoStockWebhookResource` | `POST /api/v1/zoho/stock-webhook` | `X-Zoho-Stock-Signature` | `ZohoStockWebhookHandlerService` → absolute stock reconcile |
| Shipment / package | `InternalZohoShipmentWebhookResource` | `POST /api/v1/zoho/shipment-webhook` | `X-Zoho-Shipment-Signature` or `X-Zoho-Shipment-Secret` | `ZohoShipmentWebhookHandlerService` → `ZohoOrderStatus.applyPackages` |
| Item / catalog | `InternalZohoItemWebhookResource` | `POST /api/v1/zoho/item-webhook` | `X-Zoho-Item-Signature` or `X-Zoho-Item-Secret` | `ZohoItemWebhookHandlerService` → `ZohoSyncService.pullExternalProduct` |
| Sales-order status / void | `InternalZohoOrderStatusWebhookResource` | `POST /api/v1/zoho/order-status-webhook` | `X-Zoho-Order-Status-Signature` or `X-Zoho-Order-Status-Secret` | `ZohoOrderStatusWebhookHandlerService` → `ZohoOrderStatus.applySalesOrderTracking` |
| Invoice status / void | `InternalZohoInvoiceWebhookResource` | `POST /api/v1/zoho/invoice-webhook` | `X-Zoho-Invoice-Signature` or `X-Zoho-Invoice-Secret` | `ZohoInvoiceWebhookHandlerService` → `ZohoInvoiceStatus.applyInvoice` |

Env secrets: `ZOHO_STOCK_WEBHOOK_SECRET`, `ZOHO_SHIPMENT_WEBHOOK_SECRET`, `ZOHO_ITEM_WEBHOOK_SECRET`, `ZOHO_ORDER_STATUS_WEBHOOK_SECRET`, `ZOHO_INVOICE_WEBHOOK_SECRET` (`application.zoho.*-webhook-secret`).

Scheduled `pull-order-tracking` and admin batch pulls remain as **fallback** when a Zoho workflow delivery is missed.

---

## Account — addresses, wishlist, cart — `/api/v1/account` (via commerce proxy)

Backed by `InternalAccountResource`. All endpoints require authentication. JSON keys are **snake_case**.

| Path | Method | Purpose |
|---|---|---|
| `/addresses` | GET | List saved addresses |
| `/addresses` | POST | Create address |
| `/addresses/{id}` | PUT | Update address |
| `/addresses/{id}` | DELETE | Delete address |
| `/addresses/{id}/default` | PUT | Set default address |
| `/addresses/sync` | POST | Merge device-local addresses (deduped by line1 + pincode + city) |
| `/wishlist` | GET | List wishlist product ids |
| `/wishlist/items` | POST | Add product (`product_id`) |
| `/wishlist/items/{productId}` | DELETE | Remove product |
| `/wishlist/sync` | POST | Merge guest wishlist ids into account |
| `/wishlist` | PUT | Replace entire wishlist |
| `/cart` | GET | Get persisted cart (`items`, `coupon`) |
| `/cart` | PUT | Replace cart (max 100 items; coupon optional) |

The server-side cart syncs across devices for signed-in users. Guest carts remain client-only until login, then merge via `PUT /cart` or checkout.

---

## Orders, coupons, profile, returns — `/api/v1` (via commerce proxy)

`CommerceOrdersProxyResource`, `OrderReturnsProxyResource`, `AdminOrderReturnsProxyResource`, `AdminOrdersProxyResource` proxy 1:1 to backend resources.

### Coupons & profile

| Path | Method | Purpose | Auth |
|---|---|---|---|
| `/coupons/validate` | POST | Validate coupon against subtotal | permitAll |
| `/coupons/active` | GET | List currently redeemable coupons | permitAll |
| `/account/profile` | GET/PUT | Customer profile (`full_name`, `phone`) | authenticated |
| `/account/profile/bootstrap` | POST | Seed profile name from signup email | authenticated |
| `/account/notifications` | GET | Order lifecycle notification events | authenticated |

### Orders & payments

| Path | Method | Purpose | Auth |
|---|---|---|---|
| `/orders` | POST | Create order (reserves inventory, Razorpay order or COD) | authenticated |
| `/orders/verify-payment` | POST | Verify Razorpay signature, confirm order | authenticated |
| `/orders/sync-payment` | POST | Poll Razorpay for async/UPI confirmation | authenticated |
| `/orders/retry-payment` | POST | Reopen Razorpay checkout for unpaid order | authenticated |
| `/orders` | GET | List my orders | authenticated |
| `/orders/track/{orderNumber}` | GET | Public order tracking (limited fields) | permitAll |
| `/orders/{orderNumber}` | GET | Order detail (ownership enforced) | authenticated |
| `/orders/{orderNumber}/cancel` | POST | Cancel order before dispatch (`reason`) | authenticated |
| `/payments` | GET | My payment history | authenticated |

### Customer returns & exchanges

| Path | Method | Purpose | Auth |
|---|---|---|---|
| `/order-returns` | GET | List my returns | authenticated |
| `/order-returns` | POST | Request a return (`order_number`, `reason`) | authenticated |
| `/order-returns/exchange` | POST | Request exchange (`order_number`, `order_item_id`, `reason`, `replacement_size`, `replacement_color`) | authenticated |
| `/order-returns/exchange-availability/{orderItemId}` | GET (`?size=&color=`) | Check replacement variant stock | authenticated |
| `/order-returns/config` | GET | Public return/exchange policy config | permitAll |
| `/order-returns/{returnNumber}/cancel` | POST | Cancel my return | authenticated |
| `/order-returns/eligibility/{orderNumber}` | GET | Check return eligibility | authenticated |

### Admin returns (full workflow)

| Path | Method | Purpose |
|---|---|---|
| `/admin/order-returns` | GET (`?status=`) | List all returns |
| `/admin/order-returns/{returnNumber}/approve` | POST | Approve return |
| `/admin/order-returns/{returnNumber}/reject` | POST | Reject (`rejection_reason`, optional `admin_notes`) |
| `/admin/order-returns/{returnNumber}/pickup-requested` | POST | Mark pickup requested |
| `/admin/order-returns/{returnNumber}/picked-up` | POST | Mark picked up |
| `/admin/order-returns/{returnNumber}/mark-received` | POST | Mark item received at warehouse |
| `/admin/order-returns/{returnNumber}/inspect` | POST | Mark inspected |
| `/admin/order-returns/{returnNumber}/accept` | POST | Accept after inspection |
| `/admin/order-returns/{returnNumber}/allocate-replacement` | POST | Allocate replacement item (exchange) |
| `/admin/order-returns/{returnNumber}/convert-to-refund` | POST | Convert exchange to refund |
| `/admin/order-returns/{returnNumber}/repacking` | POST | Mark repacking |
| `/admin/order-returns/{returnNumber}/ready-to-ship` | POST | Mark ready to ship |
| `/admin/order-returns/{returnNumber}/shipped` | POST | Mark shipped |
| `/admin/order-returns/{returnNumber}/delivered` | POST | Mark delivered |
| `/admin/order-returns/{returnNumber}/complete` | POST | Complete exchange |
| `/admin/order-returns/{returnNumber}/refund` | POST | Process refund (Razorpay + Ledgerly credit note) |

### Admin orders

Backed by `InternalAdminOrderResource`:

| Path | Method | Purpose |
|---|---|---|
| `/admin/orders` | GET | Paginated order list (`from`, `to`, `q`, `status`, `paymentStatus`, `paymentMethod`, `zohoSyncStatus`, `ledgerlySyncStatus`) |
| `/admin/orders/cod-pending` | GET | COD orders awaiting collection |
| `/admin/orders/{orderNumber}` | GET | Full order detail |
| `/admin/orders/{orderNumber}/mark-cod-collected` | POST | Mark COD payment collected |

---

## Domain → owning service quick reference

| Domain | Owner(s) | Path prefix |
|---|---|---|
| Auth | gateway | `/api/authenticate`, `/api/register`, `/api/account/*`, `/oauth2/**` |
| User/authority admin | gateway | `/api/admin/users`, `/api/authorities` |
| Products / categories (read) | backend via store | `/api/v1/products`, `/api/v1/categories` |
| Products / categories (admin) | backend via store | `/api/v1/admin/products`, `/api/v1/admin/categories` |
| Orders / checkout / payments | backend (logic), commerce (proxy) | `/api/v1/orders*`, `/api/v1/payments` |
| Coupons | backend, commerce | `/api/v1/coupons/*`, `/api/v1/admin/coupons` |
| Returns / exchanges | backend, commerce | `/api/v1/order-returns*`, `/api/v1/admin/order-returns*` |
| Admin orders | backend, commerce | `/api/v1/admin/orders*` |
| Account (profile, addresses, wishlist, cart) | backend, commerce | `/api/v1/account/*` |
| CMS (banners/blog/home/pages/policies/settings) | backend via store | `/api/v1/admin/{banners,blog-posts,home-sections,page-visibility,policies,site-settings}` (+ public read equivalents) |
| Ledgerly integration | backend | `/api/v1/admin/ledgerly/*`, `/internal/v1/ledgerly/webhooks` |
| Zoho Books integration | backend | `/api/v1/admin/zoho/*`, `/api/v1/admin/zoho-settings`, `/api/v1/zoho/{stock,shipment,item,order-status,invoice}-webhook` |
| Storage/uploads | backend via store | `/api/v1/admin/uploads*` |
| SKU-wise print import | backend via store | `/api/v1/admin/import/sku-wise-print` |
| Pincode lookup | backend | `/api/v1/pincode/{pincode}` |
| Gateway routing introspection | gateway | `/api/gateway/routes` |

## Related docs

- [FRONTEND_API_CONTRACT.md](./FRONTEND_API_CONTRACT.md) — existing frontend-path ↔ backend-path mapping
- [backend-architecture.md](./backend-architecture.md)
- [system-architecture.md](./system-architecture.md)
- [database-er-diagram.md](./database-er-diagram.md)
