# `mini_ms_test` — sync / pull / invoicing parity checklist (filled)

**Purpose:** Extract what we need from **`mini_ms_test`** (production store repo checkout) so we can confirm syncing, pulling, invoicing, and webhooks match Ledgerly.

**Production / source of truth repo:** `mini_ms_test` (workspace `/var/www/uat1/mini_ms_test`)  
**Ledgerly side:** `/opt/spec-driven-harmony` (`docs/ledgerly-commerce-integration.md`)  
**Local Mini reference:** `/opt/mini_and_more`

**Filled from:** workspace branch `uat3` @ `d9694b0` (2026-07-14). Secrets redacted.

---

## `mini_ms_test` (summary)

- **URL:** `https://github.com/developersqobox-org/mini_and_more.git` (workspace path `/var/www/uat1/mini_ms_test`)
- **Branch / SHA:** workspace on **`uat3`** @ `d9694b0` (2026-07-14). Remote default `origin/HEAD` → **`uat`**. `origin/prod` tip is older (`3a1df26`, 2026-05-25) — treat **this tree as Ledgerly cutover source**, not a confirmed prod deploy SHA.
- **Deployed env (from examples):** prod `https://minisnmore.com`; UAT `https://uat.minisnmore.com`
- **Zoho / Ledgerly / dual:** **Ledgerly** (columns renamed; admin UI `/admin/integrations/ledgerly`). Zoho = docs + Liquibase history + deploy leftovers.

---

## 0. Repo identity

| Field | Answer |
|-------|--------|
| Repo URL / name | `mini_and_more` / workspace `mini_ms_test` |
| Default branch (remote) | `uat` |
| This checkout | `uat3` @ `d9694b0` (2026-07-14) |
| Last deploy commit SHA + date | **Not confirmed for prod.** `origin/prod` tip `3a1df26` (2026-05-25) looks pre-cutover; this tree is post-Ledgerly. |
| Deployed env (prod domain) | `https://minisnmore.com` (UAT: `https://uat.minisnmore.com`) |
| Catalog / commerce service name + port | Catalog core holds Ledgerly client (`backend/`, port **8084** / UAT isolated **9184**). Commerce service separate (**8082** / **9182**) — not the Ledgerly HTTP client. |
| Gateway / store service name + port | Gateway **8080** / **9180**; store **8081** / **9181** |
| Still running Zoho integration service? | **Code path: N.** Cutover docs say stop after smoke. Deploy scripts may still mention `:8083`/`:9183` / `ZOHO_*`; hollow `integration/` remnant only. |
| Integration mode | **Ledgerly** (not dual) |

---

## 1. Env / config (sanitized — no live secrets)

From `backend/src/main/resources/config/application.yml` + `deploy/env/*.env.example` + live UAT `/etc/mini-and-more/uat.env`:

```text
LEDGERLY_API_BASE_URL=https://lender.qualityoutsidethebox.org/api
LEDGERLY_API_TOKEN=          # set; prefix lt_live_; length 40 (UAT host)
LEDGERLY_ORG_ID=1001         # UAT host
LEDGERLY_WEBHOOK_SECRET=     # set Y (length 48)
LEDGERLY_SYNC_PRODUCT_IMAGES=true   # UAT env; yml default false; prod.example omits
LEDGERLY_ORDER_RETRY_MS=300000
LEDGERLY_ORDER_TRACKING_MS=3600000
LEDGERLY_INVENTORY_PULL_MS=3600000
# auto-create category: DB field pull_products_auto_create_category (not env)
ZOHO_* still set on this UAT host?  No ZOHO_* in current uat.env
```

### Settings entity / DTO fields

`LedgerlySetting` / table `ledgerly_setting`:

- `apiBaseUrl`
- `apiToken`
- `orgId`
- `connected`
- `pullProductsAutoCreateCategory`
- `webhookSecret`

### Files located

- `backend/src/main/resources/config/application.yml` (`application.ledgerly.*`)
- `backend/src/main/java/com/miniandmore/app/config/ApplicationProperties.java` (Ledgerly nested; no Zoho class)
- `deploy/env/uat.env.example`
- `deploy/env/prod.env.example`

---

## 2. Commerce client — Ledgerly APIs called

**File:** `backend/src/main/java/com/miniandmore/app/client/LedgerlyIntegrationClient.java`

**Base URL:** `{LEDGERLY_API_BASE_URL}` (already includes `/api`); client paths are `/v1/...` → full `{base}/v1/integrations/...`. Trailing `/` stripped; fallback `http://127.0.0.1:8080/api`.

Auth: `Authorization: Bearer {token}`.

### Method → path mapping

| Operation | Path |
|-----------|------|
| Product sync (push / deactivate) | `POST /v1/integrations/commerce/products/sync` |
| Product pull | `POST /v1/integrations/commerce/products/pull` |
| Inventory pull | `POST /v1/integrations/commerce/inventory/pull` |
| Customer sync | `POST /v1/integrations/commerce/customers/sync` |
| Order sync (invoice) | `POST /v1/integrations/commerce/orders/sync` (+ optional `Idempotency-Key`) |
| Tracking pull | `POST /v1/integrations/commerce/orders/{externalRef}/tracking/pull` |
| Credit note (partial return) | `POST /v1/integrations/commerce/orders/{externalRef}/credit-notes` |
| Paid order cancel / full refund | `POST /v1/integrations/commerce/orders/{externalRef}/cancel` |
| Integration status | `GET /v1/platform/integration/status` |

Matches Ledgerly handbook (gateway `/api` + `/v1/...`). **No live Zoho HTTP client** in catalog/store/commerce Java.

---

## 3. Product push / pull field mapping

**Push:** `LedgerlyProductMergeService.buildPushPayload` ← `LedgerlySyncService.buildProductPayload`  
**Pull / webhook:** `mergeLedgerlyItem` / `upsertPulledProduct`

| Store field | Push to Ledgerly? | Pull from Ledgerly? | Notes |
|-------------|-------------------|---------------------|-------|
| sku | Y | Y | match key; fallback `P-{id}` |
| name | Y | create only | |
| price / selling rate | Y (`price` = compare-at if set else price) | Y | pull: Ledgerly rate → compare-at; storefront price = **80%** |
| mrp / compare_at | via selling/compare-at | Y | also `CompareAt:` in description decode |
| stock | Y | Y | |
| cost | Y (selling × **0.60**) | N | estimated |
| unit / hsn / taxPct | unit Y (`"ea"`); **hsn/tax N on product push** | **N** | order lines send `taxPct` separately |
| description | Y raw trim | Y decode | **`descriptionCodec.encode` never called on push** (docs claim otherwise) |
| category / sizes / colors / fabric / age | Y | Y | |
| imageUrl / images | Y (first image) | Y if flag | `LEDGERLY_SYNC_PRODUCT_IMAGES` |
| itemStructure / variantsJson | Y | Y | |
| active:false on delete | Y (`deactivateProduct`) | pull forces active; stale linked products deactivated | |
| slug / badges / featured | N | slug on create; badge from decode; featured store-only | |

---

## 4. Variants (size × color)

| Question | Answer / file path |
|----------|--------------------|
| Does `mini_ms_test` support `variants_json` on product? | **Y** — `product.variants_json` + `product.item_structure` |
| Liquibase / migration for variants? | `backend/src/main/resources/config/liquibase/changelog/20260714150000_add_product_variants_and_order_item_variant_sku.xml` |
| Push sends `itemStructure=VARIANTS` + `variantsJson`? | **Y** — `ProductVariantSupport.STRUCTURE_VARIANTS = "VARIANTS"` |
| Order lines store / send `variant_sku`? | **Y** — `order_item.variant_sku`; required for VARIANTS parents in `resolveLineSku` |
| Inventory reserve/return uses variant stock? | **Y** — `decrementVariantStock` / sellable resolve by sku/size/color |

**Not a gap** vs local Mini / Ledgerly on this tree.

---

## 5. Order → invoice sync (critical)

| Question | Answer |
|----------|--------|
| Trigger | Post-payment in `InternalAccountOrderResource.attemptLedgerlyOrderSync` → `ledgerlySyncService.syncOrder(orderId)` when status is `PAID` and sync is null/PENDING/FAILED |
| Payload fields | `externalRef`, `subtotal`, `discount`, `shipping`, `tax`, `donation`, `total`, `couponCode`, `pricesIncludeGst`, `paymentId`, `paymentMode`, `currency`, nested `payment{mode,paymentId,reference,amount}`, `customer{name,email,phone,shippingAddress}`, `lines[{sku,qty,rate,taxPct,description}]` |
| Idempotency header / key | `Idempotency-Key` = Razorpay payment id; one retry if invoice payment step fails (`mode must not be null`) |
| Stores `ledgerly_invoice_id` + `ledgerly_sales_order_id`? | **Y** |
| Retry of failed syncs | Scheduler every `LEDGERLY_ORDER_RETRY_MS` (default 5 min) + admin `POST .../retry-orders` |
| Still writing `zoho_invoice_id`? | **N** — renamed |

### `buildOrderPayload` excerpt

```java
// backend/.../LedgerlySyncService.java
payload.put("externalRef", order.getOrderNumber());
payload.put("total", total);
payload.put("paymentId", order.getRazorpayPaymentId());
payload.put("paymentMode", "Razorpay");
payment.put("mode", "Razorpay");
payment.put("paymentId", order.getRazorpayPaymentId());
payment.put("amount", total);
line.put("sku", resolveLineSku(item, skuByProductId)); // prefers variantSku, then catalog SKU
```

### Known local Mini behaviors — verified here

- Prefer **catalog SKU** (or variant SKU), not URL slug, on invoice lines — **Y** (slug only as last fallback)
- Sends nested `payment.mode = "Razorpay"` — **Y**
- Sends final `total` after discounts (what Razorpay charged) — **Y**

---

## 6. Tracking / fulfillment pull

| Question | Answer |
|----------|--------|
| Does `mini_ms_test` call `…/tracking/pull`? | **Y** |
| Which response field is saved on the order? | Prefers **`fulfillmentStatus` → `deliveryStatus` → `shipmentStatus` → `status`** (`LedgerlyOrderStatus.resolveFromPullResponse`) into `ledgerly_order_status`. Also saves `trackingNumber`, `trackingCarrier`, `shipmentDate`, `ledgerly_tracking_last_pulled_at` |
| Admin / customer UI shows payment status or delivery status? | Tracking fields drive delivery-oriented status (Paid/shipped/delivered ranks in `LedgerlyOrderStatus`) |
| Scheduler interval for tracking pull? | Default **3600000** ms (1h) — `LedgerlySyncScheduler.pullOpenOrderTracking` |

**Local Mini gap (payment-only `status`) is fixed on this tree.**

---

## 7. Webhooks (Ledgerly → store)

| Question | Answer |
|----------|--------|
| Webhook URL registered in Ledgerly Settings? | Configured via env/DB secret; endpoint must be registered in Ledgerly (not verified live here) |
| `mini_ms_test` endpoint path | `POST /internal/v1/ledgerly/webhooks` (`InternalLedgerlyWebhookResource`) |
| Signature header verified? | **Y** — HMAC-SHA256 hex of body; header `X-Ledgerly-Signature` (`sha256=` optional) |
| Event header | `X-Ledgerly-Event` or body `event` |
| Dedup | `ledgerly_webhook_event.event_id` |

### Events handled

| Event | Handled? |
|-------|----------|
| `item.created` | Y → merge product |
| `item.updated` | Y → merge product |
| `invoice.paid` | Y → mark PAID / set invoice id / status Paid |
| `invoice.created` | N (ignored/logged) |
| `credit_note.created` | N |
| `order.fulfilled` | Y |
| `order.delivered` | Y |
| `salesorder.fulfilled` | Y |
| `shipment.updated` | Y |
| `shipment.delivered` | Y |
| `order.cancelled` | N |
| `order.updated` | N |

Broader than local Mini (which only handled item + `invoice.paid`).

---

## 8. Credit notes / returns

| Question | Answer |
|----------|--------|
| On refund/return, calls Ledgerly credit-note API? | **Y** — `OrderReturnService` on refund when `ledgerly_credit_note_id` blank |
| Payload | `{ returnNumber, amount? }` — **no line SKUs** |
| Stores `ledgerly_credit_note_id` on return row? | **Y** (`order_return.ledgerly_credit_note_id`) |
| Stock restore local + expects Ledgerly restore? | Local restore on return flow; Ledgerly credit-note API used for accounting |
| Still creating Zoho credit notes? | **N** |

---

## 9. Admin UI & APIs

- **UI:** `/admin/integrations/ledgerly` (`src/routes/admin.integrations.ledgerly.tsx`)
- **Not** `/admin/zoho` (backup only under `routes._backup_20260611/`)
- **API base:** `/api/v1/admin/ledgerly` (`LedgerlyAdminResource`)

| Action | Method + path |
|--------|----------------|
| Integration status | `GET /integration-status` |
| Test connection | `GET /test` |
| Settings | `GET/PUT /settings` |
| Auto-create category | `PUT /pull-products-auto-create-category` |
| Sync all / one product | `POST /sync-products`, `POST /sync-products/{id}` |
| Pull products | `POST /pull-products` |
| Pull inventory | `POST /pull-inventory` |
| Retry orders | `POST /retry-orders` |
| Sync order by number | `POST /sync-orders/by-number/{orderNumber}` |
| Pull tracking | `POST /pull-order-tracking` |

---

## 10. DB columns (schema evidence)

**Rename cutover:** `backend/src/main/resources/config/liquibase/changelog/20260701130000_rename_zoho_to_ledgerly.xml`  
**Variants:** `.../20260714150000_add_product_variants_and_order_item_variant_sku.xml`  
**Webhook secret:** `.../20260701140000_ledgerly_webhook_secret.xml`

| Concept | Prod column name(s) |
|---------|---------------------|
| Item id | `ledgerly_item_id` |
| Invoice id | `ledgerly_invoice_id` |
| Sales order id | `ledgerly_sales_order_id` |
| Sync status / last synced / error | `ledgerly_sync_status`, `ledgerly_last_synced_at`, `ledgerly_sync_error` |
| Order status / tracking pulled at | `ledgerly_order_status`, `ledgerly_tracking_last_pulled_at` |
| Credit note id on return | `ledgerly_credit_note_id` |
| Product `variants_json` | `variants_json` (+ `item_structure`) |
| Order item `variant_sku` | `variant_sku` |

Also: `ledgerly_setting`, `ledgerly_webhook_event`, `category.ledgerly_category_id`. Historical Zoho changelogs remain in `master.xml` for migration order; `zoho_setting` dropped by rename changeset.

---

## 11. Docs & test scenarios in `mini_ms_test`

| Path | Role |
|------|------|
| `docs/ledgerly-integration-test-scenarios.md` | L-01–L-61 scenarios |
| `docs/ledgerly-uat-cutover.md` | Cutover runbook |
| `docs/ledgerly-uat-cutover-issues.md` | UAT issues |
| `docs/ledgerly-cutover-actions.md` | Action list |
| `docs/product-images-and-ledgerly-sync.md` | Images + sync |
| `docs/zoho-books-synced-fields.md` | **Legacy Zoho** field doc |
| `docs/zoho-integration-test-scenarios.md` | **Legacy Zoho** scenarios |
| `deploy/postman/run-ledgerly-live-api-tests.py` | Live API smoke |
| `deploy/postman/run-ledgerly-scenario-integration-tests.py` | Scenario integration |
| `jenkins/scripts/run-ledgerly-*.sh` | CI runners |

**Doc vs code mismatch:** cutover docs mention taxPct/hsn/encoded description on product push — **code does not push those**.

---

## 12. Live smoke evidence (optional)

Not queried from DB in this pass. UAT host env confirms Ledgerly wired:

- Token prefix `lt_live_`, length 40
- Org id `1001`
- Webhook secret set
- `LEDGERLY_SYNC_PRODUCT_IMAGES=true`

Suggested follow-up (one row each from UAT/prod DB):

1. Product after push: SKU, `ledgerly_item_id`, sync status
2. Paid order: order number, `ledgerly_invoice_id`, `ledgerly_sales_order_id`, payment id
3. Tracking pull JSON sample (redact PII)
4. Return row with `ledgerly_credit_note_id`
5. Webhook delivery log for one `item.updated` and one `invoice.paid`

---

## Client paths (quick table)

| Operation | `mini_ms_test` path |
|-----------|---------------------|
| Product sync | `POST /v1/integrations/commerce/products/sync` |
| Product pull | `POST /v1/integrations/commerce/products/pull` |
| Inventory pull | `POST /v1/integrations/commerce/inventory/pull` |
| Customer sync | `POST /v1/integrations/commerce/customers/sync` |
| Order sync | `POST /v1/integrations/commerce/orders/sync` |
| Tracking pull | `POST /v1/integrations/commerce/orders/{externalRef}/tracking/pull` |
| Credit note (partial return) | `POST /v1/integrations/commerce/orders/{externalRef}/credit-notes` |
| Paid order cancel / full refund | `POST /v1/integrations/commerce/orders/{externalRef}/cancel` |
| Status | `GET /v1/platform/integration/status` |

---

## Tracking

- saves: **`fulfillmentStatus` preferred, then delivery/shipment, then `status`** (not payment-only)

---

## Webhooks handled

- `item.created`, `item.updated`
- `invoice.paid`
- `order.fulfilled`, `order.delivered`, `salesorder.fulfilled`, `shipment.updated`, `shipment.delivered`
- **Not handled:** `invoice.created`, `credit_note.created`, `order.cancelled`, `order.updated`

---

## Gaps vs Ledgerly handbook / local Mini

| Area | Verdict |
|------|---------|
| Client paths | **Same page** |
| Order invoice + payment.mode + SKU | **Same page** |
| Variants | **Same page** (this tree) |
| Tracking `fulfillmentStatus` | **Fixed here** (local Mini gap closed) |
| Fulfillment webhooks | **Broader than local Mini** |
| Images | Supported; UAT env **true**; yml default false |
| Product push hsn/tax/encoded description | **Doc claim ≠ code** |
| Credit-note line SKUs | Store sends **returnNumber + amount only** |
| Zoho service | Stop-after-cutover; not used for sync in this tree |
| `invoice.created` / `credit_note.created` / `order.cancelled` webhooks | **Not handled** |

### Bottom line

This `mini_ms_test` (`uat3`) checkout is **Ledgerly-primary and largely aligned** with the commerce handbook for sync/pull/invoice/tracking/webhooks/credit notes.

**Main deltas to chase:**

1. Product-push doc drift (hsn/tax/description encode)
2. Credit-note lines omitted
3. Confirm which SHA is actually on prod (`origin/prod` tip looks pre-cutover)
