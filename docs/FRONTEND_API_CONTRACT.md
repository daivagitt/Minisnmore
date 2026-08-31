# Frontend ↔ backend API contract

All storefront and commerce calls use **`/api/v1/*`** on the **gateway** (`:8080` in dev via Vite proxy).  
Auth uses **gateway-only** paths under **`/api/*`** (no `/v1`).

Response envelope (catalog v1):

```json
{ "success": true, "data": <payload>, "meta": { "requestId": "...", "timestamp": "..." } }
```

The frontend `api` client unwraps `data` by default.

## Gateway (auth & users)

| Frontend | Method | Path |
|----------|--------|------|
| `useAuth` | POST | `/api/authenticate` → `{ id_token }` |
| `useAuth` | POST | `/api/register` |
| `useAuth` | GET | `/api/account` (unwrap: false) |
| `useAuth` | GET | `/oauth2/authorization/{provider}` |
| Admin users | * | `/api/admin/*` |

## Catalog v1 — public (no JWT)

| Frontend helper | Path |
|-----------------|------|
| `getCategories` | GET `/api/v1/categories` |
| `getProducts` | GET `/api/v1/products`, `/api/v1/products/{slug}` |
| `getBlogPosts` | GET `/api/v1/blog-posts`, `/{slug}` |
| `getBanners` | GET `/api/v1/banners` |
| `getHomeSections` | GET `/api/v1/home-sections` |
| `getPageVisibility` | GET `/api/v1/page-visibility` |
| `getSiteSettings` | GET `/api/v1/site-settings` |
| `getSubscribePage` | GET `/api/v1/subscribe-page` |
| `getPolicy` | GET `/api/v1/policies/{slug}` |
| `lookupPincode` | GET `/api/v1/pincode/{pincode}` |
| `validateCoupon` | POST `/api/v1/coupons/validate` |
| `getActiveCoupons` | GET `/api/v1/coupons/active` |
| `trackOrderPublic` | GET `/api/v1/orders/track/{orderNumber}` |

### Site settings JSON (camelCase in `data`)

Required for checkout: `gstRate`, `pricesIncludeGst`, `shippingStandardInr`, `shippingExpressInr`, `freeShippingMinInr`, plus brand/contact URLs.

Subscribe CMS fields: `subscribeHeroTitle`, … or snake_case on `/subscribe-page` only (`subscribe_hero_title`, …).

## Catalog v1 — customer (JWT)

| Frontend | Path |
|----------|------|
| `getMyProfile` / `updateMyProfile` | GET/PUT `/api/v1/account/profile` → `full_name`, `phone` |
| `bootstrapProfile` | POST `/api/v1/account/profile/bootstrap` |
| `getNotifications` | GET `/api/v1/account/notifications` |
| Addresses | GET/POST `/api/v1/account/addresses`, PUT/DELETE `/api/v1/account/addresses/{id}`, PUT `/api/v1/account/addresses/{id}/default`, POST `/api/v1/account/addresses/sync` |
| Wishlist | GET `/api/v1/account/wishlist`, POST `/api/v1/account/wishlist/items`, DELETE `/api/v1/account/wishlist/items/{productId}`, POST `/api/v1/account/wishlist/sync`, PUT `/api/v1/account/wishlist` |
| Cart | GET/PUT `/api/v1/account/cart` → `{ items, coupon }` |
| `createOrder` | POST `/api/v1/orders` |
| `verifyPayment` | POST `/api/v1/orders/verify-payment` (`orderId`, `razorpay_*`) |
| `syncPayment` | POST `/api/v1/orders/sync-payment` |
| `retryPayment` | POST `/api/v1/orders/retry-payment` |
| `listMyOrders` | GET `/api/v1/orders` → `{ orders: [...] }` |
| `getOrder` | GET `/api/v1/orders/{orderNumber}` |
| `cancelOrder` | POST `/api/v1/orders/{orderNumber}/cancel` |
| `listMyPayments` | GET `/api/v1/payments` |
| Order returns | GET/POST `/api/v1/order-returns`, POST `/api/v1/order-returns/exchange`, GET `/api/v1/order-returns/config`, GET `/api/v1/order-returns/exchange-availability/{orderItemId}`, POST `/api/v1/order-returns/{returnNumber}/cancel`, GET `/api/v1/order-returns/eligibility/{orderNumber}` |

**Order & return JSON (snake_case payloads, field tables, examples):** [order-return-json-contract.md](./order-return-json-contract.md)

## Catalog v1 — admin (JWT + ROLE_ADMIN)

| Frontend | Path prefix |
|----------|-------------|
| `backend-data.ts` CRUD | `/api/v1/admin/{categories,products,...}` |
| Ledgerly | `/api/v1/admin/ledgerly/*` (settings at `/api/v1/admin/ledgerly/settings`) |
| Zoho Books | `/api/v1/admin/zoho/*`, settings at `/api/v1/admin/zoho-settings` |
| Uploads | `/api/v1/admin/uploads`, `/presign` |
| Dashboard | GET `/api/v1/admin/dashboard` |
| Returns | `/api/v1/admin/order-returns/*` (full workflow — see [api-schema.md](./api-schema.md)) |
| Orders | `/api/v1/admin/orders/*` (list, detail, COD collection) |

## DTO naming

JHipster DTOs use **camelCase** (`compareAtPrice`, `sortOrder`, `sizesJson`).  
`backend-data.ts` maps to **snake_case** rows for the UI (`compare_at_price`, `sort_order`, …).

Orders, returns, profile, and checkout use **snake_case** in request/response bodies, except create-order / Razorpay responses which mix in camelCase (`orderId`, `orderNumber`, `razorpayOrderId`). Full field reference: [order-return-json-contract.md](./order-return-json-contract.md).

## Do not use on catalog

- `/api/categories`, `/api/products`, … (JHipster CRUD — removed; security `denyAll`)
- Direct catalog `:8081` from the browser in production
