# Zoho Books — API usage, quotas & incremental pull

Plain-language reference for **which Zoho APIs Mini's n More calls**, **what burns the daily quota**, and **how incremental product pull works**.

**Service:** catalog core (`backend/`) via `ZohoBooksClient` → Zoho Books **API v3**  
**Admin UI:** `/admin/zoho`  
**Our admin APIs:** `/api/v1/admin/zoho/**`  
**Zoho host (India):** `https://www.zohoapis.in/books/v3`  
**Official Zoho docs:** [Books API v3 introduction](https://www.zoho.com/books/api/v3/introduction/)

---

## 1. Big picture (non-technical)

```text
Admin / storefront  →  our backend (/api/v1/admin/zoho/**)  →  Zoho Books API v3
```

We do **not** call Zoho Inventory or Zoho CRM. Everything goes through **Zoho Books**.

Typical jobs:

| Job (Admin button / flow) | What happens in Zoho |
|---------------------------|----------------------|
| Connect to Zoho | OAuth login; we store a refresh token |
| Sync / push products | Create or update Books **items** (and optionally images + stock) |
| Pull products | Read Books items into Admin catalog |
| Pull inventory | Refresh stock only |
| Sync orders | Create **contact** → **sales order** → **invoice** → payment |
| Pull order tracking | Read sales order / delivery challan status |
| Approve return | Create **credit note** (and sometimes reopen inventory) |

---

## 2. How Zoho “cost” works (important)

Zoho Books does **not** bill different prices per endpoint the way Zoho CRM uses “credits”.

**Every HTTP request to Books = 1 API call** against your organisation’s limits:

| Limit | Typical value |
|-------|----------------|
| Per minute | **100** requests / org |
| Per day | Plan-based: Free **1,000** → Standard **2,000** → Professional **5,000** → Premium/Elite/Ultimate **10,000** |

If you exceed either limit, Zoho returns **HTTP 429** (error code **45**). Product pull and bulk product push stop cleanly and keep work that already succeeded; they do not continue hammering the API.

**What “costs more” for us** means **which of our flows make the most requests**, not which URL is more expensive.

---

## 3. What burns the most quota (highest → lowest)

| Rank | Flow | Why it burns quota |
|------|------|--------------------|
| 1 | **Product pull — list pagination** | **Full:** pages every active item. **Incremental:** pages **every status** (`Status.All`) on each run — list cost can match or exceed full pull when many inactive items exist. Local filtering only skips unchanged rows before detail calls. |
| 2 | **Product pull — detail & images** | **Full:** one `GET /items/{id}` per listed SKU (list omits custom fields). **Incremental:** detail for changed rows and inactive rows that require status verification. Image sync adds downloads when enabled. |
| 3 | **Bulk product push + images** | Per product: find by SKU + create/update (+ activate). Images: clear old photos + **up to 3 uploads** per item. |
| 4 | **Inventory pull** | Pages the **whole** active item list (not incremental today). |
| 5 | **Paid order sync** | Several calls per order (contact, sales order, 2 address updates, invoice, payment, mark sent). |
| 6 | **Tracking / returns** | Light: get sales order + delivery challans, or credit note. |
| — | Connection test / chart of accounts / locations / field metadata | Cheap; occasional or cached. |

**Practical tip:** Default **incremental** pull saves detail calls, not list pagination. Use **`?full=true`** when you need stale-SKU deactivation, a complete rebuild, or active items may have changed without a fresh `last_modified_time`. Keep image sync off unless you need gallery sync.

---

## 4. Incremental product pull

**Yes — implemented and used by default.**

### Behaviour

| Situation | What we do |
|-----------|------------|
| First successful pull ever (no cursor) | **Full** catalog (`filter_by=Status.Active`) |
| Later Admin “Pull products” | **Incremental** — pages all statuses, skips unchanged rows locally before detail calls |
| API `?full=true` | Force **full** catalog again |

### How it works

1. After a **successful** full or incremental pull that finishes all pages with **zero** merge failures, we save a cursor:  
   `zoho_setting.last_product_pull_at`
2. Next incremental pull pages every item status (`filter_by=Status.All`) and skips unchanged rows locally using each item's `last_modified_time` when Zoho includes it. Books does **not** support an Inventory-style `last_modified_time` list filter.
3. **Exception:** inactive list rows with a SKU are still processed when their timestamp predates the cursor. Item detail is verified before deactivation because Zoho list status can be stale; this also restores an Admin-deleted product when detail still reports it active.
4. We call **`GET /items/{id}` exactly once** for each processed row (needed for status verification and sizes/colors/custom fields). Empty detail responses and list/detail identity conflicts are failures: local data is left unchanged and the cursor does not advance.
5. **Stale deactivation** (Admin products whose SKUs vanished from Zoho) runs **only on a completed full pull** — never on incremental (would wrongly deactivate untouched products).

Additional guards reject missing item IDs and conflicting duplicate SKU/item-ID mappings, ignore exact duplicate rows, and stop on a repeated non-empty page instead of looping forever.

### Limitations

- **Active-item catch-up** (reactivation, SKU rename, price/stock changes) depends on Zoho including an accurate `last_modified_time` on list rows at or after the cursor. Rows with a stale timestamp are skipped on incremental pulls (inactive + SKU rows are the exception — see step 3).
- **`?full=true`** remains the escape hatch for a complete rebuild, stale-SKU deactivation, and any case where incremental timestamps may be unreliable.
- **Quota/outage risk:** incremental pulls still paginate the full item list (all statuses). Monitor rate-limit errors on large catalogs. HTTP 401/403/408/429, server errors, and network failures stop the run early; an incomplete or failed pull does not advance the cursor or run full-pull stale cleanup.

### Admin vs API

| Action | Incremental? |
|--------|----------------|
| Admin → **Pull products from Zoho** | Yes (default) |
| `POST /api/v1/admin/zoho/pull-products` | Yes (`full` defaults to `false`) |
| `POST /api/v1/admin/zoho/pull-products?full=true` | No — full catalog |
| Admin UI full-pull button | **Not exposed** (API only) |

### Not incremental

- **Pull inventory** — still pages the full active item list.
- Order sync / tracking / credit notes — per-document, not catalog-wide.

---

## 5. Zoho Books APIs we call (external)

Base path: `{apiHost}/books/v3`  
Auth header: `Authorization: Zoho-oauthtoken {access_token}`  
Almost all calls include `organization_id={ZOHO_ORG_ID}`.

India data center example: `https://www.zohoapis.in/books/v3`

### 5.1 OAuth (Accounts host, not Books)

| Method | Path | Purpose |
|--------|------|---------|
| GET (browser) | `/oauth/v2/auth` | User signs in / grants consent |
| POST | `/oauth/v2/token` | Exchange auth code or refresh token for access token |

### 5.2 Organisation & setup

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/organizations` | Connection test / list orgs |
| GET | `/locations` | Warehouse / location for stock |
| GET | `/chartofaccounts?filter_by=AccountType.Asset` | Inventory asset account lookup |
| GET | `/chartofaccounts?filter_by=AccountType.Expense` | Adjustment / COGS account lookup |
| GET | `/settings/fields?entity=item` | Item custom field ids (category, sizes, colors, …) |

### 5.3 Items (catalog)

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/items?sku={sku}` | Find item by SKU |
| GET | `/items?page={n}&per_page=200&filter_by=Status.Active` | List active items (full pull / inventory) |
| GET | `/items?...&filter_by=Status.All` | **Incremental** list (all statuses; filter unchanged rows locally) |
| GET | `/items/{item_id}` | Item detail (custom fields, stock, images meta) |
| POST | `/items` | Create item |
| PUT | `/items/{item_id}` | Update item |
| POST | `/items/{item_id}/active` | Mark active |
| POST | `/items/{item_id}/inactive` | Mark inactive |

### 5.4 Item images & documents

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/items/{item_id}/image` | Upload single front image (multipart) |
| DELETE | `/items/{item_id}/image` | Remove front image |
| POST | `/items/{item_id}/images` | Add one gallery photo (call once per photo; max 3) |
| DELETE | `/items/{item_id}/images` | Clear gallery photos before re-push |
| GET | `/items/{item_id}/image` | Download front image binary |
| GET | `/documents/{document_id}` | Download image document binary |

### 5.5 Contacts

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/contacts?email={email}` | Find customer by email |
| GET | `/contacts?contact_name_contains={name}` | Find customer by name |
| POST | `/contacts` | Create customer |

### 5.6 Sales orders, invoices, payments

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/salesorders` | Create sales order from paid store order |
| GET | `/salesorders/{id}` | Read SO (tracking / status) |
| PUT | `/salesorders/{id}/address/billing` | One-off billing address after create |
| PUT | `/salesorders/{id}/address/shipping` | One-off shipping address after create |
| POST | `/salesorders/{id}/status/void` | Void sales order |
| POST | `/invoices/fromsalesorder?salesorder_id={id}` | Create invoice from SO |
| GET | `/invoices/{id}` | Read invoice |
| POST | `/invoices/{id}/status/sent` | Mark invoice sent |
| POST | `/invoices/{id}/status/void` | Void invoice |
| GET | `/invoices/pdf?invoice_ids={id}` | Download invoice PDF |
| POST | `/customerpayments` | Record payment against invoice |

### 5.7 Fulfilment, returns, stock

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/deliverychallans?salesorder_id={id}` | Shipment / tracking from delivery challans |
| POST | `/creditnotes` | Credit note on approved return |
| POST | `/creditnotes/{id}/status/open` | Open credit note (inventory return path) |
| POST | `/inventoryadjustments` | Adjust on-hand qty when stock sync is enabled |

---

## 6. Our Admin APIs (what the UI / scripts call)

Base path: **`/api/v1/admin/zoho`** (admin auth required).

These endpoints talk to Zoho Books under the hood.

| Method | Our path | Zoho effect (summary) |
|--------|----------|------------------------|
| GET | `/oauth-setup` | Setup hints for API Console (no Zoho call) |
| GET | `/integration-status` | Local status; includes `lastProductPullAt` |
| GET | `/connect-url` | Builds Zoho OAuth authorize URL |
| POST | `/oauth/complete` | Exchanges code → refresh token (`/oauth/v2/token`) |
| GET | `/test` | `GET /organizations` |
| PUT | `/pull-products-auto-create-category` | Local flag only |
| POST | `/sync-products` | Push many products → items (+ images/stock if enabled) |
| POST | `/sync-products/{productId}` | Push one product |
| POST | `/pull-products` | Incremental product pull (default) |
| POST | `/pull-products?full=true` | Full product pull |
| POST | `/pull-inventory` | Stock refresh via item list |
| POST | `/retry-orders` | Sync unpaid→paid orders missing Zoho links |
| POST | `/sync-orders/{orderId}` | Sync one order |
| POST | `/sync-orders/by-number/{orderNumber}` | Sync one order by number |
| POST | `/pull-order-tracking` | Refresh tracking from SO / delivery challans |

Frontend helper for pull:

```ts
pullZohoProducts();                 // incremental
pullZohoProducts({ full: true });   // full catalog
```

---

## 7. Typical call counts (rule of thumb)

Illustrative — exact numbers vary with retries and flags.

| Operation | Rough Zoho calls |
|-----------|------------------|
| Connection test | 1 |
| Incremental pull of *N* changed SKUs | ~1 list page + **N** detail GETs (+ optional N image downloads) |
| Full pull of *N* active SKUs | ~⌈N/200⌉ list pages + **N** detail GETs (+ images) |
| Push one product (no images) | ~1–3 (find + create/update [+ active]) |
| Push one product (with images) | above + ~2 deletes + up to **3** uploads |
| Sync one paid order | ~6–10 (contact, SO, 2 addresses, invoice, payment, mark sent, …) |
| Inventory pull of *N* items | ~⌈N/200⌉ list pages (plus lookups as needed) |

Example: a **full** pull of 400 SKUs with images off ≈ **2 list + 400 detail ≈ 402 calls** — enough to blow a Free-plan **1,000**/day limit if you also push products the same day.

---

## 8. Inbound Zoho webhooks (event-driven sync)

Configured in **Zoho Books / Zoho Inventory workflow rules** (not in the store admin panel). Zoho **pushes** to our backend when something changes; we verify the signature/secret, dedupe by `eventId`, then merge into the catalog or order.

| Public endpoint | Zoho setup | What we do | Idempotency table |
|-----------------|------------|------------|-------------------|
| `POST /api/v1/zoho/stock-webhook` | Custom function / stock workflow | Reconcile absolute `stock_on_hand` for a SKU | `zoho_stock_webhook_event` |
| `POST /api/v1/zoho/shipment-webhook` | Inventory: Package Created; Shipment Updated; Shipment Delivered | Update tracking fields + status via `ZohoOrderStatus.applyPackages` | `zoho_shipment_webhook_event` |
| `POST /api/v1/zoho/item-webhook` | Item Created / Edited workflow | Single-item pull/merge via `pullExternalProduct` (no full catalog scan) | `zoho_item_webhook_event` |
| `POST /api/v1/zoho/order-status-webhook` | Sales Order status / void workflow | Apply SO status; void → local `CANCELLED` | `zoho_order_status_webhook_event` |
| `POST /api/v1/zoho/invoice-webhook` | Invoice sent / paid / void workflow | Update `zoho_order_status` and invoice id; void → local `CANCELLED` | `zoho_invoice_webhook_event` |

**Auth headers:** `X-Zoho-Webhook-Signature` or `X-Zoho-{Stock,Shipment,Item,Order-Status,Invoice}-Signature` (HMAC-SHA256 of raw JSON body), or the matching `X-Zoho-*-Secret` shared-secret header.

**Env:** `ZOHO_STOCK_WEBHOOK_SECRET`, `ZOHO_SHIPMENT_WEBHOOK_SECRET`, `ZOHO_ITEM_WEBHOOK_SECRET`, `ZOHO_ORDER_STATUS_WEBHOOK_SECRET`, `ZOHO_INVOICE_WEBHOOK_SECRET`.

**Fallback:** Admin `POST /pull-order-tracking` and the ~15 min scheduler can recover missed webhook deliveries. On UAT webhook testing, set `ZOHO_ORDER_TRACKING_ENABLED=false` to stop automatic tracking pulls so webhook behavior is visible.

---
