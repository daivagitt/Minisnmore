# Admin training and documents

**URL:** [https://minisnmore.com/admin](https://minisnmore.com/admin)  
**Who can use it:** accounts with `ROLE_ADMIN` (issued by the gateway JWT).

Sign in with the storefront account that has admin authority. After login, the left nav is the source of truth (`frontend/src/lib/admin-nav.ts`).

## Screens

| Nav label | Path | What it is for |
| --- | --- | --- |
| Dashboard | `/admin` | Overview |
| Site settings | `/admin/settings` | Contact, WhatsApp, social, footer, company |
| Banners | `/admin/banners` | Home hero carousel |
| Categories | `/admin/categories` | Catalog folders |
| Products | `/admin/products` | Catalog, SKUs, images, variants, Zoho sync |
| Home sections | `/admin/home` | Shop-by-age, reviews, featured, watch/shop reels |
| About page | `/admin/about` | Story / founders |
| Page visibility | `/admin/pages` | Hide/show storefront pages |
| Coupons | `/admin/coupons` | Promo codes |
| Discount | `/admin/discount` | Storewide percent off MRP |
| Blogs | `/admin/blog` | Posts |
| Policies | `/admin/policies` | Terms, privacy, return, shipping |
| Invoices | `/admin/invoices` | GST / billing copy |
| Orders | `/admin/orders` | Sales, status, invoices |
| Order returns | `/admin/returns` | Refund / exchange |
| Zoho settings | `/admin/zoho` | Zoho Books OAuth, pull/push, inventory |

Zoho long-running admin calls (bulk sync, pull inventory) can take several minutes. Nginx allows **600s** on `/api/v1/admin/zoho/`. Do not refresh the tab mid-sync unless the UI says it failed.

## Products — mandatory indicator

On **Products**, the editor says: **“Fields marked with * are required.”**

The red asterisk is rendered by `Field` in `frontend/src/components/admin/AdminPage.tsx` when `required` is set. Screen readers get a required hint; sighted users see `*`.

### Required on the product form

| Field | Notes |
| --- | --- |
| Name | Max length enforced in admin |
| Category | Must be an existing category |
| SKU | Unique parent stock code; “Generate” builds one from the name |
| Fabric type | Example: `100% Cotton` |
| Selling price | Required. For variants, this is derived from variant rows |
| Stock | Required. For variants, this is the sum of variant stock |
| Age (months) | Comma-separated min/max, or the age range control |

Slug is optional in the form (auto from name). MRP (`compare_at_price`) is optional; when set, selling price is computed from the storewide discount.

### Required in bulk CSV import

Template fields with `required: true`:

| Column | Example |
| --- | --- |
| `slug` | `blush-petal-bodysuit` |
| `name` | `Blush Petal Bodysuit` |
| `sku` | `MNM-BPB-001` |
| `category_slug` | Must match an existing category |
| `price` | Selling price |
| `age_range` | `0, 24` (unless `age_min_months` / `age_max_months` are set) |
| `fabric_type` | `100% Cotton` |
| `stock` | `50` |
| `active` | `TRUE` = visible on the store |

Optional but commonly used: `compare_at_price` (MRP), `images`, `details_kicker`, sizes, prints, variants.

SKU-wise print import (spreadsheet → print names / galleries) is an admin import that shells out to `deploy/scripts/prod-sku-wise-print-import.py`. The live catalog JAR still also accepts the legacy path `deploy/scripts/uat-sku-wise-print-import.py` (symlink).

### Zoho from the product row

- **Push** needs a SKU.
- **Pull** needs a Zoho item id or SKU.
- Variant pull needs each variant’s Zoho item id or SKU.

Stock and price for `VARIANTS` products are owned by the variant matrix, not the parent number fields.

## Training path (suggested)

1. Create or pick a category.
2. Create a **simple** product with all `*` fields, one image, save, confirm it appears on `/shop`.
3. Create a **variant** product (prints × sizes), confirm parent price/stock are read-only sums.
4. Place a test order on production only with an agreed test SKU and refund it.
5. Open **Orders** and **Invoices**, then **Zoho settings** and read integration status (do not fire a full catalog pull unless asked).
6. Walk **Returns** and **Coupons**.

## Related documents in this repo

| Topic | File |
| --- | --- |
| How catalog is stored and proxied | [`docs/catalog-management.md`](../catalog-management.md) |
| Pricing / Ledgerly / Zoho field mapping | [`docs/catalog-and-ledgerly-alignment.md`](../catalog-and-ledgerly-alignment.md) |
| Print names and image import | [`docs/sku-wise-print-and-ledgerly-images.md`](../sku-wise-print-and-ledgerly-images.md) |
| Size charts | [`docs/sku-wise-print-slot-mapping.md`](../sku-wise-print-slot-mapping.md) |
| Order lifecycle | [`docs/order-status-lifecycle.md`](../order-status-lifecycle.md) |
| Returns JSON | [`docs/order-return-json-contract.md`](../order-return-json-contract.md) |
| Frontend API contract | [`docs/FRONTEND_API_CONTRACT.md`](../FRONTEND_API_CONTRACT.md) |
| Zoho webhooks | [zoho-webhooks-and-details.md](./zoho-webhooks-and-details.md) |

## What not to do in production admin

- Do not run “pull all products” or “sync all to Zoho” during peak traffic without agreement (minutes-long lock, stock overwrite).
- Do not delete a live SKU that has open orders; hide it (`active` off) instead.
- Do not paste secrets into blog/policy HTML.
- Do not change Zoho OAuth redirect URLs in admin unless DNS and `/etc/mini-and-more/prod.env` already match `https://minisnmore.com`.
