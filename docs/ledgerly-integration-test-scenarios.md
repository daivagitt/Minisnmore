# Ledgerly Commerce Integration — Test Scenarios

**Product:** Mini's n More storefront (Admin + Spring Boot + Ledgerly commerce API)  
**Audience:** QA, engineering team lead, release sign-off  
**Environment:** UAT (`https://minisnmore.com`) or local (`http://localhost:5173` + gateway `:8080`)

Ledgerly replaces the former Zoho Books integration. Sync runs in **catalog core** (`backend/`, `:8084`); admin APIs are exposed via **store** (`:8081`) at `/api/v1/admin/ledgerly/**`.

---

## 1. Executive summary

The store integrates with **Ledgerly** for catalogue sync, inventory, paid-order fulfilment (invoice + sales order), shipment tracking, and **credit notes on order returns**. Integration is driven from **Admin → Integrations → Ledgerly** (`/admin/integrations/ledgerly`), **Admin → Order returns**, and background schedulers on catalog core.

| Area | Automated tests | Manual UAT required |
|------|-----------------|---------------------|
| Developer token / connection | Partial (unit) | Yes |
| Push products → Ledgerly | Partial | Yes |
| Pull products ← Ledgerly | Partial | Yes |
| Inventory pull | Partial | Yes |
| Order sync (post-payment) | Partial | Yes |
| Tracking pull | Partial | Yes |
| Credit notes (returns) | Partial | Yes |
| Failure / recovery | Partial | Yes |

---

## 2. Prerequisites

### 2.1 Ledgerly organisation

- [ ] Ledgerly deployed with **Settings → API & Webhooks**
- [ ] Developer token `lt_live_…` with commerce scopes
- [ ] Items, invoices, and credit notes enabled for the org

### 2.2 Server environment variables

| Variable | Purpose |
|----------|---------|
| `LEDGERLY_API_BASE_URL` | Ledgerly API base (e.g. `https://lender.qualityoutsidethebox.org/api`) |
| `LEDGERLY_API_TOKEN` | Developer token (`lt_live_…`) |
| `LEDGERLY_ORG_ID` | Organisation ID in Ledgerly |
| `LEDGERLY_ORDER_RETRY_MS` | Order retry scheduler interval (optional, default 300000) |
| `LEDGERLY_ORDER_TRACKING_MS` | Tracking pull scheduler interval (optional, default 3600000) |

Admin settings (`GET/PUT /api/v1/admin/ledgerly/settings`) override env when saved in the database.

### 2.3 Store prerequisites

- [ ] Admin user with `ROLE_ADMIN`
- [ ] Active products with **SKU** + category
- [ ] R2/S3 uploads working for product images
- [ ] Razorpay configured for a real **paid** test order
- [ ] Order return flow enabled; Razorpay refund credentials for admin **Issue refund**

---

## 3. Test entry points

| Entry | URL / API |
|-------|-----------|
| Admin UI | `/admin/integrations/ledgerly` |
| Integration status | `GET /api/v1/admin/ledgerly/integration-status` |
| Test connection | `POST /api/v1/admin/ledgerly/test` |
| Settings | `GET/PUT /api/v1/admin/ledgerly/settings` |
| Sync products | `POST /api/v1/admin/ledgerly/sync-products` |
| Pull products | `POST /api/v1/admin/ledgerly/pull-products` |
| Pull inventory | `POST /api/v1/admin/ledgerly/pull-inventory` |
| Retry failed orders | `POST /api/v1/admin/ledgerly/retry-orders` |
| Sync order by number | `POST /api/v1/admin/ledgerly/sync-orders/by-number/{orderNumber}` |
| Pull order tracking | `POST /api/v1/admin/ledgerly/pull-order-tracking` |

Gateway route: `/api/v1/admin/ledgerly/**` → store (`:8081`) → catalog core (`:8084`).

---

## 4. Scenarios (L-xx)

### 4.1 Connection & auth

| ID | Scenario | Steps | Expected |
|----|----------|-------|----------|
| L-01 | Create token | Ledgerly Settings → Create token | Token shown once; listed with hint |
| L-02 | Test connection | Admin → Ledgerly → Test | `connected: true` / status 200 |
| L-03 | Invalid token | Wrong Bearer on commerce API | 401 from gateway |
| L-04 | Revoked token | Revoke in Ledgerly; retry sync | 401 |

### 4.2 Products

| ID | Scenario | Expected |
|----|----------|----------|
| L-10 | Push product by SKU | `ledgerly_item_id` stored; status SYNCED |
| L-11 | Update price/stock | Ledgerly item updated by SKU |
| L-12 | Pull products | Store receives SKU, rate, stock |
| L-13 | Pull inventory | Stock levels match Ledgerly |
| L-14 | Images | Stay on store; optional description in Ledgerly |

### 4.3 Orders

| ID | Scenario | Expected |
|----|----------|----------|
| L-40 | Paid order sync | Invoice + IDs on order row (`ledgerly_invoice_id`, `ledgerly_sales_order_id`) |
| L-41 | Idempotency | Same Razorpay payment id / `Idempotency-Key` → no duplicate invoice |
| L-42 | Retry failed orders | Scheduler or admin retry succeeds |
| L-50 | Pull tracking | `ledgerly_order_status` updated |
| L-55 | Return refund | `ledgerly_credit_note_id` on return; refund still succeeds if Ledgerly down |

### 4.4 Webhooks (optional)

| ID | Scenario | Expected |
|----|----------|----------|
| L-60 | Configure webhook URL | Test delivery 200 |
| L-61 | `invoice.paid` event | Store handler updates cache |

---

## 5. Regression gate

1. Token create + test connection (L-01, L-02)
2. Product push + pull (L-10, L-12)
3. Inventory pull (L-13)
4. Paid order → invoice IDs stored (L-40)
5. Tracking pull (L-50)
6. Return → credit note (L-55)

---

## 6. Sign-off

| Role | Name | Date |
|------|------|------|
| Mini's n More ops | | |
| Ledgerly admin | | |

### Prompt 10 smoke log (2026-07-22)

| # | Step | Result | Notes |
|---|------|--------|-------|
| 1 | Test connection (`GET …/v1/platform/integration/status`) | **Fail** | HTTP **401** from `lender.qualityoutsidethebox.org` with current UAT `LEDGERLY_API_TOKEN` — regenerate token / confirm org before sync |
| 2 | Sync simple product | Blocked | Depends on step 1 |
| 3 | Sync variant product | Blocked | Depends on step 1 |
| 4 | Pull inventory | Blocked | Depends on step 1 |
| 5 | Checkout + invoice line SKU | Not run | Manual after auth fixed |
| — | Catalog core health (`:9184`) | **Pass** | HTTP 200 |
| — | Eager category fetch in `LedgerlySyncService` | **Pass** | `findOneWithEagerRelationships` / `findAllWithEagerRelationships` on deploy branch |

---

## Related docs

- [LOCAL_DEV.md](./LOCAL_DEV.md) — local stack ports and env
- [MODULES_TESTING.md](./MODULES_TESTING.md) — module verification checklist
- Ledgerly repo: `ledgerly-commerce-integration-test-scenarios.md` (full L-01…L-61 reference)
