# Zoho Books — synced fields (historical reference)

What the **legacy Zoho Books integration** synchronized between Mini's n More Admin / storefront and **Zoho Books (India)** before the **Ledgerly** cutover.

**Status:** Deprecated — replaced by Ledgerly ([ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md)).  
**Service:** `mini-prod-ms-integration` (port **9183**), classes in `integration/` JAR (`ZohoBooksSyncService`, `ZohoItemImageSync`, `ZohoStockSync`, `ZohoItemCustomFields`).  
**Admin UI:** `/admin/zoho`  
**Last updated:** 2026-06-30

---

## Architecture (legacy)

```text
Admin / storefront (catalog)  →  ZohoBooksIntegrationClient  →  integration :9183  →  Zoho Books API v3
```

Catalog stored sync metadata on DB columns named `zoho_*` (renamed to `ledgerly_*` in the 2026 cutover migration).

---

## Sync operations overview

| Operation | Direction | Trigger |
|-----------|-----------|---------|
| Push product | Admin → Zoho | Save product, **Sync to Zoho**, bulk sync |
| Pull products | Zoho → Admin | Admin **Pull products** |
| Pull inventory | Zoho → Admin | Admin **Pull inventory**, scheduler |
| Push product images | Admin → Zoho | When `ZOHO_SYNC_PRODUCT_IMAGES=true` on push |
| Pull product images | Zoho → Admin | On pull when `ZOHO_SYNC_PRODUCT_IMAGES=true` |
| Sync paid order | Store → Zoho | After Razorpay payment; retry scheduler |
| Pull order tracking | Zoho → Store | Admin action + scheduled job |
| Credit note (return) | Store → Zoho | Admin approves return + refund flow |

---

## 1. Product push (Admin → Zoho Books)

**Requires:** product **SKU** and **category** in Admin.

| Admin / store field | Zoho Books field / behaviour |
|---------------------|------------------------------|
| `name` | Item name (`item_name` / `name`) |
| `sku` | SKU (match key) |
| `price` | Selling rate (`rate` / `selling_rate`) |
| `description` | Item description; may embed `Category:`, `Sizes:`, `Colors:` lines |
| `category` | Zoho **custom field** for category (if configured) and/or category text in description |
| `sizes_json` | Zoho **Sizes** custom field and/or `Sizes:` / `Size:` lines in description |
| `colors_json` | Zoho **Colors** custom field and/or `Colors:` / `Color:` lines in description |
| `stock` | Inventory quantity when `ZOHO_SYNC_PRODUCT_STOCK=true` (inventory item type, `initial_stock` on create, quantity adjustments when on-hand differs) |
| `images_json` (first URL) | Uploaded to Zoho via `POST /items/{id}/image` when `ZOHO_SYNC_PRODUCT_IMAGES=true` |
| — | Item type set to **inventory** when stock sync enabled and inventory account configured |

**Metadata written on product row:**

| DB column (legacy) | Purpose |
|--------------------|---------|
| `zoho_item_id` | Zoho Books `item_id` |
| `zoho_sync_status` | `SYNCED`, `FAILED`, `PENDING`, etc. |
| `zoho_last_synced_at` | Last successful sync timestamp |
| `zoho_sync_error` | Last error message |

**Category row (optional):** `zoho_category_id` when category was linked in Zoho.

**Not pushed:** slug, compare-at price, badges (featured/trending/new/bestseller), sort order, age range, fabric/story CMS fields, page visibility, coupons, banners.

---

## 2. Product pull (Zoho Books → Admin)

**Match key:** SKU first; fallback `zoho_item_id`.

| Zoho Books source | Admin / store field updated |
|-------------------|----------------------------|
| Item name | `name` (on create; updates on pull merge) |
| SKU | `sku` |
| Selling rate | `price` |
| `actual_available_stock` / `available_stock` | `stock` |
| Description | `description` |
| Category custom field **or** `Category:` line in description | `category` (optional auto-create when `ZOHO_PULL_PRODUCTS_AUTO_CREATE_CATEGORY=true`) |
| Sizes custom field **or** `Sizes:` / `Size:` in description | `sizes_json` |
| Colors custom field **or** `Colors:` / `Color:` in description | `colors_json` |
| Item image (`image_uri`, `image_url`, `item_image_url`) | `images_json` → downloaded to **R2/S3** when `ZOHO_SYNC_PRODUCT_IMAGES=true` |
| — | `zoho_item_id`, `zoho_sync_status`, `zoho_last_synced_at`, `zoho_sync_error` |
| — | New items: generated `slug`, default `active=true`, default sort order, INR defaults |

**Pull side effects:**

- Full catalogue and inventory list requests use **`filter_by=Status.Active`** — inactive Zoho items are never imported as in-stock prints. Incremental product pulls include all changed statuses so newly inactive items can be deactivated locally.
- On a **full** pull, Zoho-linked matrix rows whose item IDs/SKUs are missing from the active Zoho catalog are marked **inactive**. Local-only rows are preserved, and the parent deactivates only when no active variant rows remain.
- Variant rows without parseable **size and color** (attrs / name / description) are **skipped** with an admin notice — not imported as blank PDP options.
- Image import failures are logged and surfaced as pull **notices**; price/SKU merge still proceeds.
- Items **removed from Zoho** during a full pull could mark matching Admin products **inactive**.
- Admin **delete product** could mark the linked Zoho item **inactive** in Books.

**Not pulled:** slug changes, compare-at price, badges, homepage/PDP-only content, site settings. Parallel Zoho parents with different display names (e.g. Book Style Jabla vs Book Style Tying Jabla) stay separate — pull merges within a name/`group_id` family only.

---

## 3. Inventory pull only

Updates **stock** only for products already linked (`zoho_item_id`) or matched by SKU (`findAllEligibleForZohoInventoryPull`).

Items without a usable `stock_on_hand` value are skipped so a sales-only/malformed Zoho row cannot wipe existing store stock. Variant rows are matched by stable Zoho item ID before SKU, and partial progress is retained and reported if a later page is rate-limited.

Requires **inventory tracking** enabled on the Zoho item and (for push) `ZOHO_INVENTORY_ACCOUNT_ID` / `ZOHO_DEFAULT_LOCATION_ID` when using inventory items.

---

## 4. Product images (optional)

Controlled by **`ZOHO_SYNC_PRODUCT_IMAGES=true`**.

| Direction | Behaviour |
|-----------|-----------|
| **Push** | Store image URL(s) → Zoho item image API |
| **Pull** | Zoho image binary/URL → upload to store R2 → `images_json` |

Failures are logged and shown as Admin pull notices; name/SKU/price sync still proceeds.

See also [product-images-and-ledgerly-sync.md](./product-images-and-ledgerly-sync.md).

---

## 5. Paid order sync (Store → Zoho Books)

Triggered after **paid** Razorpay checkout (`payment_status = PAID`).

| Store data | Zoho document |
|------------|---------------|
| Order number | Sales order / invoice `reference_number` |
| Customer login / email | Zoho **contact** (create or match by name/email) |
| Shipping address JSON | Contact / sales order shipping address (city, state, zip, country, phone truncated to Zoho limits) |
| Line items: product name, SKU/slug, qty, unit price | Sales order `line_items` |
| Order total, currency | Sales order / invoice totals |
| Razorpay payment id | Stored on order; used for idempotency / reconciliation |
| Coupon (if any) | Included in order/description metadata |
| India GST | Handled via `ZohoIndiaGstSupport` (place of supply, taxable flags) |

**Zoho documents created (typical flow):**

1. **Sales order**
2. **Invoice** (from sales order via `/invoices/fromsalesorder`)

**Metadata written on `customer_order`:**

| DB column (legacy) | Purpose |
|--------------------|---------|
| `zoho_sales_order_id` | Zoho sales order id |
| `zoho_invoice_id` | Zoho invoice id |
| `zoho_sync_status` | Sync state |
| `zoho_last_synced_at` | Last sync time |
| `zoho_sync_error` | Last error |
| `zoho_order_status` | High-level fulfilment status from Zoho |

Existing Zoho documents with the same `reference_number` are **linked** instead of creating a second sales order. Converting a sales order to an invoice **closes** that sales order in Zoho — that Closed document is the original, not a second order.

---

## 6. Order tracking pull (Zoho → Store)

| Zoho source | Store field |
|-------------|-------------|
| Shipment / sales order status | `zoho_order_status` (e.g. fulfilled) |
| Carrier name | `tracking_carrier` |
| Tracking number | `tracking_number` |
| Shipment date (when available) | `shipment_date` |
| — | `zoho_tracking_last_pulled_at` |

Storefront progression uses a shipment date or tracking number for **On the way**. A Zoho sales order status of `fulfilled` is not treated as delivery because invoice/payment processing can set it automatically. **Delivered** requires an explicit delivery signal, such as a package converted to a shipment and marked `fulfilled` or a delivered delivery challan.

Scheduled when `application.zoho.order-tracking.enabled=true` (default ~15 min interval). The local
`HAND_WRAPPED` step is set when a package exists for the linked Zoho sales order.

---

## 7. Returns — credit note (Store → Zoho)

On approved **order return** + refund path:

| Store data | Zoho |
|------------|------|
| Returned order + line items | **Credit note** (`/creditnotes`) |
| Return reference | Linked to original invoice / customer |

**Metadata:** credit note id stored on return row (legacy `zoho_credit_note_id`; now `ledgerly_credit_note_id` after migration).

Razorpay refund still runs in commerce; Zoho credit note was **best-effort accounting alignment**.

---

## 8. Environment flags (legacy)

| Variable | Effect |
|----------|--------|
| `ZOHO_CLIENT_ID` / `ZOHO_CLIENT_SECRET` | OAuth |
| `ZOHO_ORG_ID` | Books organisation |
| `ZOHO_DATA_CENTER` | `in` (India) default |
| `ZOHO_SYNC_PRODUCT_IMAGES` | Enable image push/pull |
| `ZOHO_SYNC_PRODUCT_STOCK` | Inventory item + stock push |
| `ZOHO_INVENTORY_ACCOUNT_ID` | Required for inventory items |
| `ZOHO_DEFAULT_LOCATION_ID` | Warehouse/location for stock |
| `ZOHO_PULL_PRODUCTS_AUTO_CREATE_CATEGORY` | Create Admin categories on pull |
| `STORAGE_*` / `S3_PUBLIC_BASE_URL` | R2 target for pulled images |

---

## 9. Fields never synced with Zoho

These stayed **Admin / storefront only**:

- Banners, blog posts, home sections, page visibility, site settings, policies, coupons (CMS)
- Product slug, compare-at price, badges, sort order (except defaults on pull-create)
- Age range, fabric/story, gallery ordering beyond image import
- Customer profiles, wishlists, cart (except as part of paid order payload)
- Razorpay secrets, JWT, OAuth for storefront login

---

## 10. Ledgerly equivalent (current)

After cutover, the same *business* flows use Ledgerly with similar (not identical) fields. See:

- [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md) — L-10…L-55
- [product-images-and-ledgerly-sync.md](./product-images-and-ledgerly-sync.md) — images + `LEDGERLY_SYNC_PRODUCT_IMAGES`
- DB columns renamed: `zoho_*` → `ledgerly_*` (`20260701130000_rename_zoho_to_ledgerly.xml`)

| Legacy Zoho column | Ledgerly column |
|--------------------|-----------------|
| `zoho_item_id` | `ledgerly_item_id` |
| `zoho_sync_status` | `ledgerly_sync_status` |
| `zoho_sales_order_id` | `ledgerly_sales_order_id` |
| `zoho_invoice_id` | `ledgerly_invoice_id` |
| `zoho_order_status` | `ledgerly_order_status` |
| `zoho_category_id` | `ledgerly_category_id` |

---

## Related docs

- [zoho-books-api-usage.md](./zoho-books-api-usage.md) — Zoho endpoints we call, daily quotas, which flows burn calls, incremental product pull
- [zoho-integration-test-scenarios.md](./zoho-integration-test-scenarios.md) — deprecated Z-01…Z-71 QA checklist
- [ledgerly-uat-cutover.md](./ledgerly-uat-cutover.md) — cutover notes
- Integration Javadocs (archived): `artifacts/javadocs/integration/`
