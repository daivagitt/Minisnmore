# Mini’s n More — Catalog, inventory & Ledgerly alignment (production)

**Audience:** Engineers deploying or maintaining `mini_ms_test` / `mini_and_more` against Ledgerly.  
**Ledgerly reference:** `/opt/spec-driven-harmony` — `docs/ledgerly-commerce-integration.md`, `docs/minis-n-more-variants-and-composite-items.md`, `docs/prod-repo-sync-parity-checklist.md`.  
**Last updated:** 2026-07-22

---

## One-sentence summary

**Catalog core** (`backend/`) owns products, categories, and **storefront stock** in Postgres. The browser never talks to catalog directly — traffic is **Gateway → Store → Catalog**. **Ledgerly** is the accounting/inventory counterpart: catalog core pushes/pulls via the Ledgerly commerce API; after link, treat **Ledgerly stock as source of truth** for pull/inventory sync.

---

## Services & ports

| Service | Directory | Port (prod / UAT isolated) | Owns catalog DB? |
|---------|-----------|----------------------------|------------------|
| Gateway | `services/gateway/` | `8080` / `9180` | No |
| Store (edge API) | `services/store/` | `8081` / `9181` | No |
| Commerce | `services/commerce/` | `8082` / `9182` | No |
| **Catalog core** | `backend/` | **`8084` / `9184`** | **Yes** |

- **DB:** Postgres `miniandmore` (Liquibase runs **only** on catalog core).
- Store/commerce call catalog via `APPLICATION_CATALOG_BASE_URL` / `CATALOG_BASE_URL` (e.g. `http://127.0.0.1:8084`).
- **No separate Zoho/Ledgerly integration microservice** — all Ledgerly HTTP lives in catalog core.

---

## Request flow

```text
Browser (Vite / React)
    → Gateway (:8080 / :9180)
    → Store (:8081 / :9181)     /api/v1/**
    → Catalog core (:8084 / :9184)   /internal/v1/**
```

| Piece | Path |
|-------|------|
| Store catch-all | `services/store/.../StoreCatalogProxyResource.java` |
| Path rewrite | `services/store/.../CatalogCoreClient.java` — `/api/v1/X` → `/internal/v1/X` |
| Exception | `/api/v1/admin/ledgerly/**` stays on catalog as `/api/v1/admin/ledgerly/**` |

---

## Product data model (catalog)

Entity: `backend/.../domain/Product.java` → table `product`.

| Area | Fields |
|------|--------|
| Identity | `id`, `slug` (unique), `sku` (unique), `name` |
| **Pricing** | `price` = **selling** (storefront); `compare_at_price` = **MRP** / list |
| **Stock** | `stock` — for variant products, **sum of active variant rows** |
| Content | `description`, `story`, `fabric_type`, `images_json`, `badge`, age min/max, flags |
| Options | `sizes_json`, `colors_json` |
| **Colour images** | `color_images_json` — Ledgerly map `{ "Print 1": ["url1","url2"], ... }` synced with embedded images in `colors_json` |
| **Variants** | `item_structure` (`SIMPLE` \| `VARIANTS`) + `variants_json` (JSON matrix) |
| Tax (DB) | `hsn`, `tax_pct` (order lines send `taxPct`; included on product push when set) |
| Category | FK → `category` (lazy — **must eager-fetch for Ledgerly sync**) |
| Ledgerly | `ledgerly_item_id`, `ledgerly_sync_status`, `ledgerly_last_synced_at`, `ledgerly_sync_error` |

**Category** (`category`): `slug`, `name`, `description`, `image_url`, `sort_order`, `active`, `ledgerly_category_id`.

Frontend snake_case via `frontend/src/lib/backend-data.ts` (`compare_at_price`, `item_structure`, `variants_json`, …).

> **Schema note:** Mini **does** store `item_structure` as a DB column (Liquibase `20260714150000_…`). Treat non-empty `variants_json` + `item_structure=VARIANTS` as the variant mode; empty matrix → `SIMPLE`.

---

## Pricing contract (Mini ↔ Ledgerly) — **authoritative**

| Mini (store) | Meaning | Ledgerly (inventory item) | Commerce API field |
|--------------|---------|---------------------------|-------------------|
| `price` | Selling (customer pays) | `rate` | `price` on push; `price` on pull |
| `compare_at_price` | MRP / strikethrough | `mrp` | `mrp` / `compareAtPrice` on push; `mrp` on pull |
| `cost` (estimated) | Not stored on product | `cost` | Push: **selling × 0.60** (estimate) |
| Variant `rate` | Selling for size×color | Variant `rate` | Inside `variantsJson` |
| Variant `mrp` | List for combo | Variant `mrp` | Inside `variantsJson` |

**Default when only MRP is known:** selling = **MRP × (1 − store_discount_percent / 100)** from `site_setting.store_discount_percent` (default **20%**). Applied at runtime on storefront, checkout, and Zoho push via `StorePricingService`.

**Do not** map MRP → Ledgerly `rate`. Stored variant `rate` is not used for customer pricing when variant `mrp` is present — runtime derivation from MRP wins.

Implementation: `LedgerlyProductMergeService.buildPushPayload` / `mergeLedgerlyItem`.

### Implementation status (this checkout — verify before prod)

As of 2026-07-22 on `uat3`:

| Area | Status |
|------|--------|
| **Prompt 1** — eager category on sync | Done (`findOneWithEagerRelationships` / `findAllWithEagerRelationships`) |
| **Prompt 2** — admin Selling / MRP labels | Done in source + UAT frontend rebuilt |
| **Prompt 4** — HSN / tax on push | Done when `hsn` / `tax_pct` non-null |
| **Prompt 6** — inventory pull counts | Done (INFO log + admin summary) |
| **Prompt 7** — order line SKU size×color fallback | Done + test |
| **Pricing push/pull merge** | **Done** (2026-07-22) — selling→`price`/rate, MRP→`mrp` |
| **SKU auto-generation (admin)** | **Done** — parent **Generate** + matrix refresh (Ledgerly parity) |
| **Storefront variant OOS** | Done (`product.$slug.tsx`, per-variant stock) |
| **Colour images (`color_images_json`)** | Done — DB column, admin save sync, Ledgerly push/pull + R2 import |

~~Merge code may still follow **legacy** pricing~~ — resolved in `LedgerlyProductMergeService` + `ProductVariantSupport` (legacy variant rows with rate-only still supported via ×0.80 split).

---

## Variants (size × color)

| Piece | Path |
|-------|------|
| Migration | `backend/.../liquibase/changelog/20260714150000_add_product_variants_and_order_item_variant_sku.xml` |
| Backend | `ProductVariantSupport.java` |
| Merge / push | `LedgerlyProductMergeService.java` |
| Admin UI | `frontend/src/components/admin/VariantsMatrixEditor.tsx` |
| Storefront | `frontend/src/lib/product-variants.ts` |

### Rules

- **Non-variant:** `item_structure=SIMPLE` (or empty matrix) → single SKU, parent `price` / `stock`.
- **Variant product:** `item_structure=VARIANTS` + matrix in `variants_json`; each row: `sku`, `size`, `color`, `rate` (selling), `mrp`, `cost`, `stock`, `active`.
- **Parent SKU:** optional before save — **Generate** from product name (e.g. `Baby Swaddle` → `BABY-SWADDLE`) or auto-filled on save when blank (`suggestParentSku` in `admin-variants.ts`).
- **Variant SKUs:** `PARENT-SIZE-COLOR` via **Fill missing SKUs**, **Generate / refresh matrix** (size × colour cross-product), or **Regenerate variant SKUs** when parent SKU changes.
- Existing matrix rows **keep stock/prices** when refreshing; only missing combos are added.
- Push sends `itemStructure: "VARIANTS"` + `variantsJson` when matrix non-empty.
- Non-variant: prefer omitting `itemStructure` or send `SIMPLE` (Ledgerly treats as **SINGLE** / simple item).
- Parent `stock` = sum of active variant stock; rebuilt on save and after each variant stock change.
- **Unique** variant SKUs; variant SKU ≠ parent SKU (same as Ledgerly).
- Checkout: `order_item.variant_sku` + invoice line `sku` = **variant SKU**, not parent.
- **Legacy matrix rows:** if a row has only `rate` (no `mrp` / `price`), storefront treats `rate` as MRP and derives selling as ×0.80. New rows should set `rate` = selling and `mrp` = list.

### Variant JSON example

```json
[
  {
    "id": "a1",
    "sku": "HORIZON-M-NAVY",
    "size": "M",
    "color": "Navy",
    "rate": 1039.20,
    "mrp": 1299,
    "cost": 500,
    "stock": 4,
    "active": true
  }
]
```

---

## How inventory is managed **on Mini** (store stock)

Mini does **not** use Ledgerly warehouses. Stock is an **integer on `product.stock`** (and per-row in `variants_json`).

### Who uses which stock?

| Context | Simple product | Variant product |
|---------|----------------|-----------------|
| Storefront availability | Parent `stock` | **Variant row `stock`** (size × colour) |
| Checkout reserve | Parent `stock` | **Variant row** → parent sum recalculated |
| Admin “Stock” field | Editable parent | **Read-only sum** of matrix rows |
| Ledgerly push | Parent `stock` + matrix | Parent sum + **`variantsJson`** (per-row stock) |
| Ledgerly inventory pull | Parent `stock` | See **pull rules** below |

### Where stock changes

| Event | What happens | File |
|-------|----------------|------|
| **Checkout reserve** | Deduct parent or variant stock before order persisted | `InternalAccountOrderResource.reserveInventory` |
| **Unpaid reservation timeout** | Restore stock if payment not completed | `InternalAccountOrderResource` release scheduler |
| **Return approved** | Restore qty to variant or parent | `OrderReturnService.restoreInventory` |
| **Admin edits product** | Manual stock / matrix edit | Admin save |
| **Ledgerly inventory pull** | **Overwrites** Mini stock from Ledgerly | `LedgerlySyncService.pullInventory` |
| **Ledgerly product pull / webhook** | Merges stock + optional `variantsJson` | `LedgerlyProductMergeService.mergeLedgerlyItem` |

### Reserve logic (checkout)

1. Load product with **`findOneForInventoryUpdate`** (row lock).
2. If variants matrix present → resolve sellable row by variant SKU or size×color → **`decrementVariantStock`** (parent sum updated via `aggregates()`).
3. Else → deduct from parent `stock`.
4. Returns / reservation release use **`incrementVariantStock`** or parent increment.
5. Insufficient stock → HTTP 409 with variant-level message when applicable.

### Ledgerly inventory pull — variant vs parent

`LedgerlySyncService.pullInventory` applies each inventory level in order:

| Pull payload | Product match | Effect on Mini |
|--------------|---------------|----------------|
| **`variantsJson`** on level | Parent (by parent SKU or `ledgerlyItemId`) | Full matrix replace; per-row stock + parent sum |
| **`stock` only**, SKU = **variant SKU** | Parent found via variant SKU scan | **`updateVariantStockBySku`**; parent sum recalculated |
| **`stock` only**, SKU = **parent SKU** | VARIANTS parent | **Skipped** (intentional — parent-only stock would not update size×colour rows) |
| **`stock` only** | SIMPLE parent | Parent `stock` overwritten |

**Operational rule for variant products:** maintain stock in Ledgerly **per variant SKU**, or return **`variantsJson`** on pull. Parent-only stock levels for VARIANTS items are counted as success but do **not** change Mini variant rows.

### Stock source of truth

| Phase | Source of truth |
|-------|-----------------|
| Live storefront sales | **Mini** (immediate reserve on checkout) |
| After **Pull inventory** or hourly scheduler | **Ledgerly** overwrites linked products |
| Accounting / invoices | **Ledgerly** (order sync creates invoice; stock movement on Ledgerly side via commerce) |

**Operational rule:** After changing stock in Ledgerly Items UI, run **Pull inventory** on Mini (or wait for `LEDGERLY_INVENTORY_PULL_MS`, default 1h). After heavy Mini-only admin stock edits, run **Sync products** to push to Ledgerly.

---

## Ledgerly integration (catalog core only)

### Config (env + `ledgerly_setting`)

```text
LEDGERLY_API_BASE_URL=https://<ledgerly-host>/api
LEDGERLY_API_TOKEN=lt_live_…
LEDGERLY_ORG_ID=<must match API key org in Ledgerly>
LEDGERLY_WEBHOOK_SECRET=…
LEDGERLY_SYNC_PRODUCT_IMAGES=true|false
LEDGERLY_ORDER_RETRY_MS=300000
LEDGERLY_ORDER_TRACKING_MS=3600000
LEDGERLY_INVENTORY_PULL_MS=3600000
```

Auth: `Authorization: Bearer {token}`. Mini does **not** send `X-Organization-Id` today; Ledgerly gateway typically mints JWT with `organizationId` from the API key.

### API mapping

| Operation | Ledgerly path | Mini trigger |
|-----------|---------------|--------------|
| Product push | `POST /v1/integrations/commerce/products/sync` | Admin save; Sync products; deactivate on delete |
| Product pull | `POST /v1/integrations/commerce/products/pull` | Admin Pull products |
| Inventory pull | `POST /v1/integrations/commerce/inventory/pull` | Admin + `LedgerlyInventoryPullScheduler` |
| Order → invoice | `POST /v1/integrations/commerce/orders/sync` | Post-payment + retry scheduler |
| Tracking pull | `POST /v1/integrations/commerce/orders/{externalRef}/tracking/pull` | Scheduler + admin |
| Credit note | `POST /v1/integrations/commerce/orders/{externalRef}/credit-notes` | Return refund |
| Webhooks | `POST /internal/v1/ledgerly/webhooks` | `item.created` / `item.updated`, fulfillment events, `invoice.paid` |

### Key Java classes

| Role | Class |
|------|--------|
| HTTP client | `LedgerlyIntegrationClient.java` |
| Sync orchestration | `LedgerlySyncService.java` |
| Field mapping | `LedgerlyProductMergeService.java` |
| Admin REST | `LedgerlyAdminResource.java` |
| Credentials | `LedgerlyCredentialsProvider.java` |

### Push field mapping (store → Ledgerly) — **target**

| Store | Push? | Ledgerly |
|-------|-------|----------|
| `sku`, `name` | Y | `sku`, `name` |
| `price` | Y | `rate` (via commerce `price`) |
| `compare_at_price` | Y | `mrp`, `compareAtPrice` |
| `stock` | Y | `stock` |
| `variants_json` | Y if matrix | `variantsJson` + `itemStructure=VARIANTS` |
| `description` + category/fabric/sizes/… | Y | `description` (and/or structured fields) |
| First image URL | Y if enabled | `imageUrl` |
| `color_images_json` | Y when set | `colorImagesJson` (map: colour name → URL list) |
| `cost` | Y | estimated selling × 0.60 |
| `hsn`, `tax_pct` on product | Y when set | `hsn`, `taxPct` |
| `slug`, featured flags | N | store-only |

### Pull field mapping (Ledgerly → store) — **target**

| Ledgerly | Store |
|----------|-------|
| `price` / `rate` | `price` (selling) |
| `mrp` | `compare_at_price` |
| `stock` | `stock` |
| `variantsJson` | `variants_json` + rebuild sizes/colors |
| `description` | decode → category, fabric, sizes, story, … |
| `ledgerlyItemId` | `ledgerly_item_id` |
| `colorImagesJson` | `color_images_json` (+ merges into `colors_json` for storefront) |
| Inactive items | omitted from product pull; stale linked store products can be deactivated |

### Order line SKU (critical)

`LedgerlySyncService.resolveLineSku`:

1. Prefer `order_item.variant_sku`
2. Else if parent is VARIANTS → lookup sellable row by **size×color** on parent matrix
3. Else parent `product.sku`
4. VARIANTS with no resolvable variant → **fail** order sync (`IllegalStateException`)

Ledgerly invoice lines **must** receive variant SKU when the product has a matrix.

---

## Admin & storefront surfaces

| Screen | Route | Notes |
|--------|-------|-------|
| Products | `/admin/products` | Variants matrix, selling vs MRP |
| Categories | `/admin/categories` | Local; category name pushed with product |
| Ledgerly | `/admin/integrations/ledgerly` | Sync / pull / retry |
| Shop | `/shop`, `/product/$slug` | Search `?q=`; variant price & **OOS from matrix row stock** (size disabled when all colours OOS) |

**On product save:** async/sync push to Ledgerly.  
**On product delete:** `notifyLedgerlyBeforeStoreDelete` (deactivate in Ledgerly if linked) then delete local row.

---

## Images

- URLs in `product.images_json` (R2/S3). **Not** synced bidirectionally by default.
- Push may send **first** image as `imageUrl` when `LEDGERLY_SYNC_PRODUCT_IMAGES=true`.
- Storefront images remain **Mini-owned**.

See [`docs/product-images-and-ledgerly-sync.md`](./product-images-and-ledgerly-sync.md).

---

## Production deploy checklist (Mini + Ledgerly)

### Ledgerly (must be live first)

- [ ] Platform + inventory deployed with commerce mesh fixes (org header on platform → inventory, `ROLE_SERVICE` on `/api/items/**`).
- [ ] Developer token `lt_live_…` with commerce scopes; **organizationId** matches `LEDGERLY_ORG_ID` on Mini.
- [ ] Gateway URL reachable from catalog VPS (`LEDGERLY_API_BASE_URL` includes `/api`).

### Mini catalog

- [ ] Liquibase applied (including `item_structure`, `variants_json`, `order_item.variant_sku`, Ledgerly rename migration).
- [ ] `LEDGERLY_*` in `/etc/mini-and-more/*.env`; restart catalog core with `--update-env`.
- [ ] Sync uses **`findOneWithEagerRelationships` / `findAllWithEagerRelationships`** for category (required with `open-in-view: false`).
- [ ] PM2/nginx: catalog `:8084`, store proxy healthy.
- [ ] Pricing push/pull matches the **authoritative** table (not legacy MRP-as-rate).

### Smoke (production)

| # | Step | Expected |
|---|------|----------|
| 1 | Admin → Ledgerly → Test connection | `connected: true` |
| 2 | Sync one **simple** product (SKU, selling, MRP, stock) | `ledgerly_sync_status=SYNCED`, item in Ledgerly |
| 3 | Sync one **variant** product | Ledgerly **Contains Variants**, variant SKUs unique |
| 4 | Pull products | Rows update; no “no products” if step 2–3 succeeded |
| 5 | Pull inventory | Linked SKUs stock matches Ledgerly |
| 6 | Place test order (variant line) | `variant_sku` set; order sync → Ledgerly invoice line SKU = variant |
| 7 | Return flow | Mini stock restored; credit note in Ledgerly if configured |

---

## Known failure modes

| Symptom | Cause | Fix |
|---------|-------|-----|
| `403 on POST http://inventory/api/items` | Ledgerly platform missing org header or inventory ACL | Redeploy Ledgerly platform + inventory (see Ledgerly repo fixes) |
| `Could not initialize proxy [Category#…]` | Lazy category outside session during sync | Use eager product fetch in `LedgerlySyncService` (fixed in repo) |
| Pull products: **0 items** | Nothing synced yet, or Ledgerly inventory GET still 403/empty | Fix auth; push products first |
| Duplicate SKU errors | Same SKU on two products or variant = parent | Enforce unique SKUs in admin |
| Store price wrong after pull | Confused MRP vs selling | Apply pricing contract above |
| Variant OOS wrong after Ledgerly stock change | Pull sent parent SKU stock only for VARIANTS item | Use per-variant SKU or `variantsJson` on inventory pull |
| UAT admin still shows “UAT price” | Stale frontend `frontend/dist/` | Rebuild + restart `mini-prod-frontend` |

---

## Related docs

| Doc | Purpose |
|-----|---------|
| [`docs/catalog-management.md`](./catalog-management.md) | Topology / services / admin surfaces |
| [`docs/product-images-and-ledgerly-sync.md`](./product-images-and-ledgerly-sync.md) | Images |
| [`docs/ledgerly-uat-cutover.md`](./ledgerly-uat-cutover.md) | Cutover runbook |
| [`docs/catalog-microservice-split.md`](./catalog-microservice-split.md) | Service split |
| [`docs/mini_ms_test-ledgerly-parity-checklist.md`](./mini_ms_test-ledgerly-parity-checklist.md) | Parity audit fill-in |
| Ledgerly `docs/minis-n-more-variants-and-composite-items.md` | Ledgerly-side variants |
| Ledgerly `docs/prod-repo-sync-parity-checklist.md` | Parity audit |

---

## Key files (cheat sheet)

| Area | Path |
|------|------|
| Product entity | `backend/.../domain/Product.java` |
| Variant logic | `backend/.../ProductVariantSupport.java` |
| Ledgerly merge | `backend/.../LedgerlyProductMergeService.java` |
| Ledgerly sync | `backend/.../LedgerlySyncService.java` |
| Checkout reserve | `backend/.../InternalAccountOrderResource.java` |
| Product repository (eager) | `backend/.../ProductRepository.java` |
| FE catalog | `frontend/src/lib/backend-data.ts`, `frontend/src/lib/product-variants.ts` |
| Admin Ledgerly | `frontend/src/routes/admin.integrations.ledgerly.tsx` |

---

# Implementation prompts (copy into Cursor on Mini repo)

Use these **after** Ledgerly platform/inventory fixes are deployed. Run in **`/opt/mini_and_more`** (or your `mini_ms_test` checkout).

---

## Prompt 1 — Verify Ledgerly sync uses eager category load

**Status:** Done (2026-07-22).

```text
In mini_and_more catalog core, audit LedgerlySyncService for any product load that touches product.getCategory() without eager fetch. Ensure syncProduct, notifyLedgerlyBeforeStoreDelete, and syncAllProducts use ProductRepository.findOneWithEagerRelationships / findAllWithEagerRelationships. Update LedgerlySyncServiceTest mocks to match. Run ./mvnw -Dtest=LedgerlySyncServiceTest test in backend/.
```

---

## Prompt 2 — Admin UI: selling vs MRP labels

**Status:** Done in source; UAT frontend rebuilt 2026-07-22.

```text
In mini_and_more frontend admin products (admin.products.tsx and VariantsMatrixEditor.tsx), ensure labels match the Ledgerly contract: "Selling price" maps to product.price / variant.rate; "MRP" maps to compare_at_price / variant.mrp. Remove any copy that says MRP is pushed as Ledgerly rate. Add a one-line help tooltip referencing docs/catalog-and-ledgerly-alignment.md pricing table.
```

---

## Prompt 3 — Send X-Organization-Id only if Ledgerly requires it (optional hardening)

```text
Review LedgerlyIntegrationClient. If Ledgerly gateway expects X-Organization-Id on commerce calls, add header from ledgerly_setting.orgId or LEDGERLY_ORG_ID when set. Do not break auth when org id is blank. Add a unit test on header presence when orgId configured.
```

---

## Prompt 4 — HSN / tax on product push (if accounting needs it)

**Status:** Done (2026-07-22).

```text
Extend LedgerlyProductMergeService.buildPushPayload to include product.hsn and product.taxPct when non-null, mapping to commerce sync fields Ledgerly inventory accepts. Add LedgerlyProductMergeServiceTest assertions. Document in catalog-and-ledgerly-alignment.md push table.
```

---

## Prompt 5 — Post-deploy smoke script

```text
Add backend/src/test/java/.../LedgerlySyncSmokeIT.java or a deploy/scripts/ledgerly-sync-smoke.sh that: (1) checks GET /v1/platform/integration/status with LEDGERLY_API_TOKEN, (2) documents expected successCount for sync-products against one fixture SKU. Keep it non-destructive for prod (read-only or dry-run flag).
```

---

## Prompt 6 — Inventory pull observability

**Status:** Done (2026-07-22).

```text
In LedgerlySyncService.pullInventory, log summary counts (success, failure, skipped unlinked) at INFO when admin triggers pull. Surface the same counts in LedgerlyAdminResource response so /admin/integrations/ledgerly shows "updated N, failed M" instead of silent partial failure.
```

---

## Prompt 7 — Order line SKU regression test

**Status:** Done (2026-07-22).

```text
Add LedgerlySyncServiceTest case: product with variants_json, order line with size/color but empty variant_sku — assert buildOrderPayload lines[].sku resolves to variant SKU not parent SKU. Match resolveLineSku in LedgerlySyncService.
```

---

## Prompt 8 — Replace outdated internal catalog doc

**Status:** Done — `docs/catalog-management.md` links here for pricing/inventory.

```text
If the team maintains a separate "how catalog is managed" doc, replace its pricing and item_structure sections with a link to docs/catalog-and-ledgerly-alignment.md. Remove incorrect rows: MRP → Ledgerly rate, variant rate = MRP, item_structure SIMPLE enum unless added to DB.
```

---

## Prompt 9 — Ledgerly side (run on spec-driven-harmony)

```text
On Ledgerly spec-driven-harmony: confirm platform OrganizationHeaderRestTemplateInterceptor forwards X-Organization-Id on mesh calls; inventory SecurityConfiguration allows ROLE_SERVICE on /api/items/**; redeploy platform and inventory to UAT/prod. Update docs/ledgerly-commerce-integration.md pricing table: price→rate, compareAtPrice→mrp (not cost).
```

---

## Prompt 10 — End-to-end UAT

**Status:** Partial — catalog health OK; Ledgerly token returned **401** on 2026-07-22 (sync blocked until token refreshed). See `docs/ledgerly-integration-test-scenarios.md`.

```text
Execute minis-n-more UAT: (1) sync simple product, (2) sync variant product with 2 SKUs, (3) pull inventory, (4) checkout one variant, (5) verify Ledgerly invoice line SKU and stock movement. Record results in docs/ledgerly-integration-test-scenarios.md with pass/fail and dates.
```
