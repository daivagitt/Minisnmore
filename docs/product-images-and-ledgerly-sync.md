# Product images and Ledgerly / Zoho sync

> **Updated 2026-08-03:** Per-print galleries and Ledgerly `colorImagesJson` sync are now implemented.  
> **See the full guide:** [sku-wise-print-and-ledgerly-images.md](./sku-wise-print-and-ledgerly-images.md)  
> **Swatch fallback chain:** [swatch-url-fallback.md](./swatch-url-fallback.md)  
> **PDP gallery, derivatives & LazyImage:** [pdp-gallery-and-lazy-images.md](./pdp-gallery-and-lazy-images.md)

How product photos reach the Mini's n More storefront, what Ledgerly sync includes, and what the legacy Zoho integration used to do.

**UAT stack:** Ledgerly (Zoho Books integration service stopped).  
**Last updated:** 2026-08-03 (pointer added; details in linked doc)

---

## Short answer (current behaviour)

**Per-print lifestyle galleries can sync from Ledgerly** via `colorImagesJson` (downloaded to R2 when `LEDGERLY_SYNC_PRODUCT_IMAGES=true`).

**Print swatches** (`swatchUrl`) are **not** part of the Ledgerly protocol — upload in Admin or use `prod-restore-print-swatch-images.py`.

**Listing hero** can sync via Ledgerly `imageUrl`.

For the full data model, xlsx import flow, **swatch mapping**, API schema, and implementation plan, read **[sku-wise-print-and-ledgerly-images.md](./sku-wise-print-and-ledgerly-images.md)** (see §3 Swatch mapping).

---

## How images appear on the website

```text
Admin → Products (upload) → R2/S3 → images_json in DB → Storefront API → PDP / gallery / cart
```

1. An admin uploads images on the **Products** page (or sets image URLs).
2. Uploads go to R2 via `S3_*` settings in `/etc/mini-and-more/prod.env`.
3. Public URLs are stored on the product record (`images_json`).
4. The storefront loads products from `/api/v1/products` and maps:
   - First image → main `image`
   - All images → `gallery`

If a product has **no** images in Admin, the UI may fall back to a placeholder — not an image from Ledgerly/Zoho.

**Code references:**

- Storefront mapping: `frontend/src/lib/storefront.ts` (`mapProduct`)
- Product field: `backend/.../domain/Product.java` (`imagesJson` / `images_json`)
- Admin upload: `/api/v1/admin/uploads` → `InternalStorageUploadResource`

---

## What Ledgerly sync includes

| Field | Push (Admin → Ledgerly) | Pull (Ledgerly → Admin) | Webhook `item.updated` |
|-------|-------------------------|-------------------------|-------------------------|
| SKU | Yes | Yes | Yes |
| Name | Yes | Yes | — |
| Price | Yes | Yes | Yes |
| Stock | Yes | Yes | Yes |
| Description | Yes | Yes | — |
| Category (from `Category:` in description) | — | Yes (optional auto-create) | — |
| **Images** | **No** | **No** | **No** |

Implementation: `LedgerlySyncService` (`buildProductPayload`, `upsertPulledProduct`, `LedgerlyWebhookHandlerService.handleItemUpdated`).

Documented in [ledgerly-uat-cutover.md](./ledgerly-uat-cutover.md) under **R2 / images**.

---

## Recommended workflow today

1. **Ledgerly / Zoho Books** — manage SKU, rate, stock, invoices, orders.
2. **Admin → Products** — upload product photos for the website.
3. Match products by **SKU** so pull/sync updates price and stock on the correct row.
4. After changing photos in Admin, **save the product**; no Ledgerly action is required for images.

**Direction of images:**

| Direction | Supported? |
|-----------|------------|
| Website (Admin) → Ledgerly | No |
| Ledgerly → Website | No |

---

## Legacy Zoho image pull (not active on UAT)

The old **Zoho integration** service (`mini-prod-ms-integration`, port 9183) included `ZohoItemImageSync`:

- On **pull products** from Zoho Books, with `ZOHO_SYNC_PRODUCT_IMAGES=true`:
  - Download the Zoho item image
  - Upload to store R2 (`STORAGE_*` / `S3_PUBLIC_BASE_URL`)
  - Set the product’s image URLs in the catalog DB

Requirements (historical):

- Zoho Books: **Item Image** enabled (Settings → Items → Field Customization)
- Env: `ZOHO_SYNC_PRODUCT_IMAGES=true`
- R2/S3 configured for imported images

See [zoho-books-synced-fields.md](./zoho-books-synced-fields.md) (section 4) and [zoho-integration-test-scenarios.md](./zoho-integration-test-scenarios.md) (section 2.1–2.3).

**UAT status:** `mini-prod-ms-integration` is **stopped**; cutover is to Ledgerly. This Zoho image path is **not** running.

---

## Future: Ledgerly image sync

To mirror the old Zoho behavior for Ledgerly, development would need to:

1. Expose item image URL or binary from the Ledgerly API.
2. On pull or webhook (`item.updated`), download the image for the matching SKU.
3. Upload to Mini's n More R2 and update `images_json`.

This is **not implemented** today. Ledgerly does **not** need the store’s R2 credentials for current commerce sync.

---

## Related docs

- [zoho-books-synced-fields.md](./zoho-books-synced-fields.md) — **full legacy Zoho field sync reference** (push/pull/orders)
- [ledgerly-uat-cutover.md](./ledgerly-uat-cutover.md) — cutover checklist and R2 note
- [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md) — smoke tests (R2 uploads prerequisite)
- [zoho-integration-test-scenarios.md](./zoho-integration-test-scenarios.md) — legacy Zoho image pull flags
