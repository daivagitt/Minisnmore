# SKU-wise print mapping, image uploads & Ledgerly integration

**Audience:** Engineers implementing Ledgerly commerce sync and maintaining per-print product images on Mini's n More UAT/production.

**Last updated:** 2026-08-03

---

## One-sentence summary

**Print names, sizes, and stock** come from the SKU WISE PRINT spreadsheet (or Ledgerly variants). **Lifestyle galleries** can sync from Ledgerly via `colorImagesJson`. **Print swatches** are store-only (admin upload or xlsx restore script). All images live in **Cloudflare R2**; the catalog DB stores URLs in `colors_json`, `color_images_json`, and `images_json`.

---

## Table of contents

1. [Data model (3 image layers)](#1-data-model-3-image-layers)
2. [Flow A — SKU-wise print spreadsheet](#2-flow-a--sku-wise-print-spreadsheet)
3. [Swatch mapping (detailed)](#3-swatch-mapping-detailed)
4. [Flow B — Admin image upload](#4-flow-b--admin-image-upload)
5. [Flow C — Ledgerly sync](#5-flow-c--ledgerly-sync)
6. [Ledgerly API schema (implement this)](#6-ledgerly-api-schema-implement-this)
7. [Recommended implementation plan](#7-recommended-implementation-plan)
8. [UAT scripts reference](#8-uat-scripts-reference)
9. [Environment flags](#9-environment-flags)
10. [Code references](#10-code-references)
11. [Related docs](#11-related-docs)

---

## 1. Data model (3 image layers)

Each product row in Postgres stores images in **three JSON fields**:

| DB column | API field | Purpose | Example |
|-----------|-----------|---------|---------|
| `colors_json` | `colorsJson` | Per-print **swatch** + **lifestyle gallery** | See below |
| `color_images_json` | `colorImagesJson` | **Ledgerly gallery map** (lifestyle only, no swatches) | `{"PINK & BEE":["https://…/1.png","https://…/2.png"]}` |
| `images_json` | `imagesJson` | **Listing hero** (one URL for shop cards / trending) | `["https://…/hero.jpg"]` |

### `colors_json` shape (rich per-print mode)

```json
[
  {
    "name": "PINK & BEE",
    "swatchUrl": "https://pub-….r2.dev/uat/products/…-PINK-BEE-swatch.jpg",
    "imageUrls": [
      "https://pub-….r2.dev/uat/products/…-pink-bee-1.png",
      "https://pub-….r2.dev/uat/products/…-pink-bee-2.png"
    ]
  },
  {
    "name": "AQUA & BEE",
    "swatchUrl": "https://…",
    "imageUrl": "https://…/single-gallery.jpg"
  }
]
```

Rules enforced by `ProductColorImagesSupport`:

- **`swatchUrl`** — print picker on PDP; lives in `colors_json` **only** (never in `color_images_json`).
- **`imageUrl` / `imageUrls`** — PDP main carousel (lifestyle photos); max **10 per print**.
- **`color_images_json`** — derived map `{ printName: [galleryUrls…] }` for Ledgerly push/pull; **excludes swatches**.

Plain string colours (`["Navy","Blush"]`) are supported for simple products without per-print media.

---

## 2. Flow A — SKU-wise print spreadsheet

**Script:** `deploy/scripts/prod-sku-wise-print-import.py`  
**Input:** `SKU WISE PRINT  FINAL DETAILS AS ON 25-07-26.xlsx` (repo root)

This flow is **separate from Ledgerly**. It builds **print names, sizes, stock, and the size×print variant matrix**.

```text
xlsx (main sheet + PRINT NAMES + WHC)
    → build_print_name_map()     # swatch photo → print name
    → build_products()           # variants + stock
    → PUT /api/v1/admin/products # upsert by parent SKU
    → merge_preserve_media()     # keep existing swatches/galleries
    → PostgreSQL
```

### Step 1 — Resolve print names from swatch photos

The main sheet (`Final updated count - 21-07-26`) does **not** contain print names as text in Print columns — only **embedded swatch images** in `Print-1` … `Print-11`.

The import script:

1. Reads swatch images from the **PRINT NAMES** sheet (photo + human-readable name).
2. Matches each product row’s swatch photo by **MD5 hash**, then **perceptual hash** (Hamming distance ≤ 10) if bytes differ slightly.
3. Applies **manual overrides** in `PRINT_NAME_MANUAL_OVERRIDES` when auto-match fails (e.g. PINK & BEE on Front Open Vest Half Sleeve).

Result: each `(excel_row, print_slot)` maps to a real name like `"PINK & BEE"`, `"AQUA & BEE"`, `"BABY BLUE"`.

### Step 2 — Build variant matrix

For each style (parent SKU, e.g. `FRONT-OPEN-VEST-HALF-SLEEVE`):

| Source | Field |
|--------|-------|
| Style name | Product name + slug |
| `Size` column | Sizes list (`0-3 M`, `3-6 M`, …) |
| Resolved print names | Colours / prints list |
| `Print-N QUANTITY` | Stock per size×print |
| Output | `itemStructure: VARIANTS`, `variantsJson` |

Each variant row:

```json
{
  "sku": "FRONT-OPEN-VEST-HALF-SLEEVE-0-3-M-PINK-BEE",
  "size": "0-3 M",
  "color": "PINK & BEE",
  "stock": 24,
  "rate": 799,
  "mrp": 999,
  "active": true
}
```

**Special case:** *Side button T Shirt (Half sleeve)* uses the **WHC 21-7-26** sheet for print/stock (not main-sheet Print columns). *Book Style Tying Jabla* uses the **main sheet only** (WHC override was removed — it caused BLUE/FISH vs BABY BLUE/BIRD drift).

### Step 3 — Upsert without wiping images

On update, `merge_preserve_media()` keeps existing:

- `swatchUrl`, `imageUrl`, `imageUrls` per print
- `images_json` listing hero
- `color_images_json` when print names realign

Re-running the import refreshes **names / stock / variants** but does **not** delete uploaded galleries.

### Run on UAT

```bash
set -a && source /etc/mini-and-more/prod.env && set +a
GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \
  python3 deploy/scripts/prod-sku-wise-print-import.py
```

Optional: `--dry-run` to preview without writing.

Swatch **names** come from Step 1 above. Swatch **image URLs** (`swatchUrl`) are applied separately — see [§3 Swatch mapping](#3-swatch-mapping-detailed).

### Link orphan R2 galleries by filename

```bash
python3 deploy/scripts/prod-restore-galleries-from-r2.py
```

---

## 3. Swatch mapping (detailed)

Swatch mapping is a **two-stage** process:

| Stage | What it does | Script | Output |
|-------|----------------|--------|--------|
| **A. Print name resolution** | Main-sheet swatch photo → human print name | `prod-sku-wise-print-import.py` | `colorsJson` names + `variantsJson[].color` |
| **B. Swatch URL restore** | PRINT NAMES artwork → R2 upload → `swatchUrl` on each print | `prod-restore-print-swatch-images.py` | `colorsJson[].swatchUrl` |

Stage A does **not** upload images. Stage B does **not** change stock or variant SKUs.

```text
┌─────────────────────────────────────────────────────────────────┐
│  SKU WISE PRINT xlsx                                            │
│  ┌──────────────────┐    ┌──────────────────┐                   │
│  │ Main sheet       │    │ PRINT NAMES sheet │                   │
│  │ Print-1…11 =     │    │ Col D = name      │                   │
│  │ embedded swatch  │───▶│ Col C = swatch    │                   │
│  │ photos (no text) │    │ photo (master)    │                   │
│  └────────┬─────────┘    └────────┬─────────┘                   │
│           │ image match            │                            │
│           ▼                        ▼                            │
│  build_print_name_map()      build_print_name_images()          │
│  (row, slot) → "PINK & BEE"  "PINK & BEE" → JPEG bytes          │
└───────────┬────────────────────────┬────────────────────────────┘
            │                        │
            ▼                        ▼
     uat-sku-wise-print-import   uat-restore-print-swatch-images
            │                        │
            ▼                        ▼
     colorsJson: ["PINK & BEE",…]   swatchUrl: https://…/…-swatch.jpg
     variantsJson: color field      (preserves imageUrls galleries)
```

### 3.1 Spreadsheet structure

File: `SKU WISE PRINT  FINAL DETAILS AS ON 25-07-26.xlsx` (repo root)

| Sheet | Purpose |
|-------|---------|
| `Final updated count - 21-07-26` | Styles, sizes, **embedded swatch photos** in Print columns, quantities |
| `PRINT NAMES` | **Master catalogue** of print names + one swatch image each |
| `WHC 21-7-26` | Override print/stock for Side button T-shirt only |

### 3.2 PRINT NAMES sheet (master swatch catalogue)

Each row defines one print:

| Column | Content |
|--------|---------|
| B | Print number (reference) |
| C | **Embedded swatch image** (the artwork file) |
| D | **Human print name** (e.g. `PINK & BEE`, `BABY BLUE`, `HAPPY BEE`) |

The restore script reads column **D** as the display name and column **C** as the image bytes.

**Known gap:** Some names exist in column D but have **no image** in column C (e.g. `PINK & BEE` on row 14). Those prints must use a manual override on the main sheet (see §3.5) or an admin swatch upload.

### 3.3 Main sheet — Print column layout

Print slots are **not text cells** — they contain pasted swatch photos only.

| Print slot | Image column (0-based) | Quantity column |
|------------|------------------------|-----------------|
| Print-1 | col 7 | col 8 |
| Print-2 | col 9 | col 10 |
| … | `col = 2 × N + 5` | next col |
| Print-11 | col 27 | col 28 |

Formula used in code: `col = 2 * print_index + 5` where `print_index` is 1…11.

For each `(excel_row, print_index)` that has an embedded image, the import script resolves a name by matching that image against the PRINT NAMES catalogue.

### 3.4 Image matching algorithm (`build_print_name_map`)

Used by **`prod-sku-wise-print-import.py`** to turn silent swatch photos into print names.

1. **Build reference index** from PRINT NAMES sheet:
   - For each row with name + image: compute **MD5** of image bytes.
   - Also compute **8×8 perceptual hash** (average hash) for fuzzy matching.

2. **Scan main sheet** embedded images (drawing1.xml):
   - For each Print-N photo on a product row:
     - Try **exact MD5** match → print name from PRINT NAMES.
     - Else compute perceptual hash; find closest PRINT NAMES entry with **Hamming distance ≤ 10**.
     - If still no match → slot is skipped (may become `Print 3` placeholder or qty-only row).

3. **Apply manual overrides** (`PRINT_NAME_MANUAL_OVERRIDES`) — always wins.

Current manual overrides in `prod-sku-wise-print-import.py`:

```python
PRINT_NAME_MANUAL_OVERRIDES = {
    (5, 3): "BABY BLUE",           # Loop Back Fleece Vest 12-18M
    (118, 5): "COMBO 5",           # Face Napkin
    (118, 7): "COMBO 7",           # Face Napkin (no image)
    (124, 2): "AQUA",              # Baby Swaddle
    (124, 3): "BROWN SWADDLE",
    (124, 4): "WHITE SWADDLE",
    (124, 5): "GREEN SWADDLE",
    (72, 3): "AQUA",               # Baby Front Open Frock 0-3M (qty, no photo)
    (37, 1): "PINK & BEE",         # Front Open Vest Half Sleeve (all sizes)
    (38, 1): "PINK & BEE",
    (39, 1): "PINK & BEE",
    (40, 1): "PINK & BEE",
}
```

Key: `(excel_row_number, print_index_1_to_11)` → `"PRINT NAME"`.

**Adding a new override:** When auto-match picks the wrong name (e.g. `AQUA & BLUE` instead of `PINK & BEE`), add the main-sheet row number and print slot to this dict, then re-run the import.

### 3.5 Swatch URL restore (`prod-restore-print-swatch-images.py`)

After print **names** exist on products, this script uploads swatch **files** and sets `swatchUrl`.

#### Step 1 — Build print image catalogue

`build_print_name_images(xlsx)` reads PRINT NAMES sheet:

```
norm_key("PINK & BEE") → ("PINK & BEE", raw_image_bytes)
```

One entry per unique print name (first row wins).

#### Step 2 — Upload to R2

For each print name needed by any product in the catalog:

1. Convert image to **JPEG** (quality 92).
2. Upload via `POST /api/v1/admin/uploads` with folder `products`.
3. Filename pattern: `{Print-Name-sanitized}-swatch.jpg`  
   Example: `PINK-BEE-swatch.jpg` inside R2 key `uat/products/{timestamp}-{uuid}-PINK-BEE-swatch.jpg`.

#### Step 3 — Map print name → swatch URL on products

For each product’s `colorsJson` entries:

1. Look up `norm_key(color.name)` in upload map.
2. If missing, try **`SWATCH_NAME_ALIASES`** (short WHC/matrix labels → full PRINT NAMES label):

```python
SWATCH_NAME_ALIASES = {
    "pink": "pink buds",      # matrix says PINK → artwork is PINK BUDS
    "blue": "baby blue",      # matrix says BLUE → artwork is BABY BLUE
    "bee": "happy bee",       # matrix says BEE → artwork is HAPPY BEE
    "fish": "aqua",           # Book Style Jabla FISH → AQUA artwork
}
```

3. Set `entry["swatchUrl"] = url` if changed.
4. **Preserve** existing `imageUrl` / `imageUrls` (lifestyle galleries are not touched).
5. Read rich `colors_json` from **Postgres directly** (admin list API may omit gallery URLs).
6. `PUT /api/v1/admin/products/{id}`.

#### Import script media aliases (`merge_preserve_media`)

When re-importing the xlsx, existing swatches/galleries are kept when print names change:

```python
COLOR_MEDIA_ALIASES = {
    "baby blue": ["blue"],
    "aqua": ["fish"],
    "pink & bee": ["aqua & blue"],
}
```

If a product print is renamed from `AQUA & BLUE` → `PINK & BEE`, gallery URLs and swatches copy across via alias lookup.

### 3.6 What gets stored in the DB

After both stages:

```json
{
  "name": "PINK & BEE",
  "swatchUrl": "https://pub-….r2.dev/uat/products/…-PINK-BEE-swatch.jpg",
  "imageUrls": [
    "https://pub-….r2.dev/uat/products/…-pink-bee-1.png",
    "https://pub-….r2.dev/uat/products/…-pink-bee-2.png"
  ]
}
```

| Field | Source | In `color_images_json`? |
|-------|--------|-------------------------|
| `swatchUrl` | PRINT NAMES restore / admin upload | **No** |
| `imageUrls` | Admin upload / R2 restore / Ledgerly `colorImagesJson` | **Yes** (galleries only) |

Backend rule (`ProductColorImagesSupport`): swatches never enter `color_images_json` or Ledgerly push payload.

### 3.7 Run swatch restore

```bash
set -a && source /etc/mini-and-more/prod.env && set +a
GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \
  python3 deploy/scripts/prod-restore-print-swatch-images.py
```

Options:

- `--dry-run` — preview uploads/updates without writing
- `--file /path/to/xlsx` — use a specific spreadsheet

### 3.8 Full refresh sequence (names + swatches + galleries)

```bash
# 1. Print names, sizes, stock, variants (preserves existing media)
python3 deploy/scripts/prod-sku-wise-print-import.py

# 2. Swatch URLs from PRINT NAMES sheet → R2 → swatchUrl
python3 deploy/scripts/prod-restore-print-swatch-images.py

# 3. Lifestyle galleries from R2 filenames → imageUrls
python3 deploy/scripts/prod-restore-galleries-from-r2.py
```

Run step 2 **after** step 1 whenever Ledgerly pull or xlsx import resets `colorsJson` to plain names (swatches dropped).

### 3.9 Admin manual swatch upload (alternative)

Merchandisers can upload swatches in **Admin → Products → Print swatches** without running scripts:

1. Upload file → R2.
2. Auto-save sets `colorsJson[].swatchUrl`.
3. Same DB field as script restore.

Use when a print has no image in PRINT NAMES sheet or override is not yet in code.

### 3.10 Ledgerly and swatches

| Direction | Swatches |
|-----------|----------|
| Ledgerly → UAT | **Not supported** — pull sends plain `colors` strings only |
| UAT → Ledgerly | **Not sent** — `buildPushPayload()` excludes `swatchUrl` |
| After Ledgerly pull | Re-run `prod-restore-print-swatch-images.py` or admin re-upload |

If you extend Ledgerly later, a sensible field would be `swatchImagesJson: { "PINK & BEE": "https://…" }` — **not implemented today**.

### 3.11 Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Print shows as `Print 3` | No swatch photo or no PRINT NAMES match | Add `PRINT_NAME_MANUAL_OVERRIDES` or fix xlsx image |
| Wrong print name (e.g. AQUA & BLUE vs PINK & BEE) | Perceptual hash matched wrong artwork | Add manual override for `(row, slot)` |
| Print name OK but no swatch on PDP | Restore script not run, or name missing from PRINT NAMES sheet | Run `prod-restore-print-swatch-images.py` or admin upload |
| WHC product missing swatch | Matrix uses `PINK` but sheet has `PINK BUDS` | Covered by `SWATCH_NAME_ALIASES`; add alias if new case |
| Swatches vanished after Ledgerly pull | Pull overwrites `colorsJson` with plain strings | Re-run swatch restore |
| Gallery OK but swatch wrong | Swatch and gallery are independent fields | Re-upload swatch only in admin |

---

## 4. Flow B — Admin image upload

Used when merchandisers upload photos in **Admin → Products**.

```text
Admin UI (ColorImagesEditor)
    → POST /internal/v1/admin/uploads  (multipart → R2)
    → auto-save (saveWithColorGallery / saveWithColorSwatch)
    → PUT /api/v1/admin/products/{id}
    → ProductService.normalizeVariants()
    → sync color_images_json ↔ colors_json
    → PostgreSQL
```

### Upload types

| Upload | Stored as | Auto-save? |
|--------|-----------|------------|
| Print **swatch** | `colorsJson[].swatchUrl` | Yes (after upload) |
| Print **gallery** (lifestyle) | `colorsJson[].imageUrls` | Yes (after upload) |
| Product-level images | `imagesJson` | On manual Save |

### R2 path pattern

```
uat/products/{timestamp}-{uuid}-{sanitized-filename}.png
```

Public base URL: `S3_PUBLIC_BASE_URL` (e.g. `https://pub-….r2.dev`).

### Storefront behaviour

- **PDP carousel** — lifestyle URLs for the **selected print only** (no swatch in main gallery).
- **Print picker** — `swatchUrl` per print.
- **Shop cards** — first lifestyle from first print with a gallery, else first swatch, else `images_json[0]`.
- **Out-of-stock sizes** — strikethrough + diagonal cross (not blurred); cannot be selected.

---

## 5. Flow C — Ledgerly sync

### Will UAT accept per-print images from Ledgerly?

**Yes — for lifestyle galleries**, via `colorImagesJson`.

On **pull** or **webhook** (`item.updated`), `LedgerlyProductMergeService.mergeLedgerlyItem()`:

1. Sets `colors` from Ledgerly (plain print names).
2. If `colorImagesJson` / `color_images_json` is present:
   - Downloads each URL (when `LEDGERLY_SYNC_PRODUCT_IMAGES=true`).
   - Re-uploads to store R2.
   - Writes `color_images_json` and merges galleries into `colors_json`.
3. If `imageUrl` is present — sets listing hero in `images_json`.

**Implementation:** `LedgerlyProductMergeService.java`, `LedgerlyItemImageSync.java`

### Push (store → Ledgerly)

`buildPushPayload()` sends:

| Field | Content |
|-------|---------|
| `sku`, `name`, `price`, `mrp`, `stock`, … | Standard merchandising |
| `colors` | Comma-separated print names |
| `sizes` | Comma-separated sizes |
| `variantsJson` | Full size×print matrix |
| `imageUrl` | First listing hero |
| `colorImagesJson` | `{ printName: [lifestyleUrls…] }` — **swatches excluded** |

### What Ledgerly supports vs does not

| Feature | Ledgerly pull/push? | Where it lives otherwise |
|---------|---------------------|--------------------------|
| Per-print **lifestyle gallery** | ✅ `colorImagesJson` | Admin upload |
| Single **listing hero** | ✅ `imageUrl` | Admin / derived from galleries |
| Per-print **swatch** | ❌ Not in protocol | Admin or `prod-restore-print-swatch-images.py` |
| Print name from xlsx swatch photo | ❌ | `prod-sku-wise-print-import.py` only |
| Size×print **stock matrix** | ✅ `variantsJson` + inventory pull | xlsx import |

### Important: print name matching

Keys in `colorImagesJson` **must exactly match** print names in:

- Ledgerly `colors` field
- Each `variantsJson[].color`
- Store `colors_json[].name`

Examples that must match character-for-character:

- `"PINK & BEE"` ✅
- `"Pink Bee"` ❌ (won’t merge)

### Pull side-effect on swatches

Ledgerly pull sets `colorsJson` to a **plain string array** of print names. Existing `swatchUrl` values are **not** sent from Ledgerly and can be lost unless:

- You re-run `prod-restore-print-swatch-images.py`, or
- Merchandisers re-upload swatches in admin, or
- You extend Ledgerly to send swatches (not implemented today).

Galleries from `colorImagesJson` **are** re-applied on every pull.

---

## 6. Ledgerly API schema (implement this)

### Item payload (pull / webhook / push)

```json
{
  "ledgerlyItemId": "9103",
  "sku": "FRONT-OPEN-VEST-HALF-SLEEVE",
  "name": "Front Open Vest Half Sleeve",
  "price": 639.2,
  "mrp": 799,
  "compareAtPrice": 799,
  "stock": 349,
  "unit": "ea",
  "currency": "INR",
  "active": true,
  "itemStructure": "VARIANTS",
  "fabricType": "100% cotton Interlock 180 GSM",
  "category": "VEST",
  "ageMinMonths": 0,
  "ageMaxMonths": 24,
  "sizes": "0-3 M, 3-6 M, 6-12 M, 12-18 M, 18-24 M",
  "colors": "AQUA & AQUA, AQUA & BEE, AQUA & BLUE, PINK & AQUA, PINK & BEE",
  "imageUrl": "https://your-cdn.example.com/listing-hero.jpg",
  "colorImagesJson": {
    "PINK & BEE": [
      "https://your-cdn.example.com/pink-bee-1.png",
      "https://your-cdn.example.com/pink-bee-2.png"
    ],
    "AQUA & BEE": [
      "https://your-cdn.example.com/aqua-bee-1.png"
    ]
  },
  "variantsJson": [
    {
      "sku": "FRONT-OPEN-VEST-HALF-SLEEVE-0-3-M-PINK-BEE",
      "size": "0-3 M",
      "color": "PINK & BEE",
      "stock": 24,
      "rate": 799,
      "mrp": 999,
      "cost": 0,
      "active": true
    }
  ]
}
```

### Field notes

| Field | Type | Required | Notes |
|-------|------|----------|-------|
| `sku` | string | Yes | Parent SKU; unique key for upsert |
| `ledgerlyItemId` | string | Recommended | Secondary link if SKU changes |
| `itemStructure` | `"SIMPLE"` \| `"VARIANTS"` | Yes for matrix products | |
| `colors` | string or JSON array | Yes for prints | Comma-separated print names |
| `sizes` | string or JSON array | Yes for sized products | |
| `variantsJson` | array | Yes when `VARIANTS` | One row per size×print |
| `colorImagesJson` | object | Optional | `{ printName: string[] }` lifestyle URLs only |
| `imageUrl` | string | Optional | Listing hero (replaces slot 0 on pull) |
| `color_images_json` | object | Alias | Same as `colorImagesJson` |

### Inventory pull payload (stock-only update)

```json
{
  "inventory": [
    {
      "sku": "FRONT-OPEN-VEST-HALF-SLEEVE",
      "ledgerlyItemId": "9103",
      "variantsJson": [
        { "sku": "FRONT-OPEN-VEST-HALF-SLEEVE-0-3-M-PINK-BEE", "stock": 20 }
      ]
    }
  ]
}
```

When parent product is `VARIANTS`, prefer **`variantsJson` per line SKU** — UAT updates matrix stock without wiping images or rates.

### Image import behaviour (UAT)

When `LEDGERLY_SYNC_PRODUCT_IMAGES=true`:

- External URLs in `imageUrl` and `colorImagesJson` are downloaded and uploaded to R2.
- Filenames: `{parentSku}-ledgerly.jpg`, `{parentSku}-{color-slug}-{n}.jpg`.
- Already-on-R2 URLs are kept as-is (no re-download).
- Failures are logged; SKU/price/stock sync still succeeds.

When `LEDGERLY_SYNC_PRODUCT_IMAGES=false`:

- URLs are stored as-is (passthrough).

---

## 7. Recommended implementation plan

### Phase 1 — Ledgerly item master

1. Parent SKU matches store (e.g. `BOOK-STYLE-TYING-JABLA`).
2. **Prints** stored as “colors” in Ledgerly (`BABY BLUE`, `BIRD`, `AQUA` — not generic BLUE/FISH).
3. **Variant rows**: every size×print with stock, rate, MRP.
4. `itemStructure: "VARIANTS"`.

### Phase 2 — Images in Ledgerly

1. One `imageUrl` per parent (listing hero).
2. `colorImagesJson`: map print name → array of gallery URLs (up to 10 per print in store).
3. Do **not** put swatch URLs in `colorImagesJson` — store treats them as lifestyle photos.

### Phase 3 — Sync to UAT

1. **Product pull** or **webhook** → merges fields + imports galleries to R2.
2. Run swatch restore if needed: `prod-restore-print-swatch-images.py`.
3. **Inventory pull** on schedule → updates `variantsJson` stock only.

### Phase 4 — Store → Ledgerly push

After admin uploads galleries, store push sends `colorImagesJson` back to Ledgerly so both systems stay aligned.

### Avoid

- Mixed naming (WHC vs main sheet) for the same style — caused Jabla stock drift.
- Ledgerly pull as sole source for swatches — not supported.
- Mismatched print names between `colors`, `variantsJson.color`, and `colorImagesJson` keys.

---

## 8. UAT scripts reference

| Script | Purpose |
|--------|---------|
| `deploy/scripts/prod-sku-wise-print-import.py` | Prints, sizes, stock, variants from xlsx |
| `deploy/scripts/prod-restore-print-swatch-images.py` | Swatch URLs from xlsx PRINT NAMES → R2 → DB |
| `deploy/scripts/prod-restore-galleries-from-r2.py` | Link orphan R2 files to prints by filename |
| `deploy/scripts/prod-realign-print-color-names.py` | Fix `colors_json` vs `color_images_json` name drift |

Typical refresh sequence after xlsx changes:

```bash
python3 deploy/scripts/prod-sku-wise-print-import.py
python3 deploy/scripts/prod-restore-print-swatch-images.py
python3 deploy/scripts/prod-restore-galleries-from-r2.py
```

---

## 9. Environment flags

UAT (`/etc/mini-and-more/prod.env`):

```bash
# Ledgerly connection
LEDGERLY_API_BASE_URL=https://lender.qualityoutsidethebox.org/api
LEDGERLY_SYNC_PRODUCT_IMAGES=true   # download Ledgerly images → R2 on pull/webhook

# R2 storage
S3_PUBLIC_BASE_URL=https://pub-….r2.dev
S3_BUCKET=…
S3_ACCESS_KEY=…
S3_SECRET_KEY=…
```

---

## 10. Code references

| Area | Path |
|------|------|
| Ledgerly merge (pull/push) | `backend/.../LedgerlyProductMergeService.java` |
| Ledgerly image import | `backend/.../LedgerlyItemImageSync.java` |
| Colour/gallery sync | `backend/.../ProductColorImagesSupport.java` |
| Product save / variant normalize | `backend/.../ProductService.java` |
| Ledgerly pull/push orchestration | `backend/.../LedgerlySyncService.java` |
| Admin upload UI | `frontend/src/components/admin/ColorImagesEditor.tsx` |
| Admin save + `colorImagesJson` build | `frontend/src/lib/backend-data.ts` (`saveProduct`) |
| Storefront gallery per print | `frontend/src/lib/product-gallery.ts` |
| Swatch restore from xlsx | `deploy/scripts/prod-restore-print-swatch-images.py` |
| Print name image matching | `deploy/scripts/prod-sku-wise-print-import.py` (`build_print_name_map`) |
| R2 upload API | `backend/.../InternalStorageUploadResource.java` |

---

## 11. Related docs

- [catalog-and-ledgerly-alignment.md](./catalog-and-ledgerly-alignment.md) — services, ports, sync direction
- [ledgerly-integration-test-scenarios.md](./ledgerly-integration-test-scenarios.md) — smoke tests
- [ledgerly-uat-cutover.md](./ledgerly-uat-cutover.md) — UAT cutover checklist
- [product-images-and-ledgerly-sync.md](./product-images-and-ledgerly-sync.md) — legacy note (see this doc for current behaviour)
- [swatch-url-fallback.md](./swatch-url-fallback.md) — swatch URL lookup, UI fallback chain, listing hero rules

---

## Quick FAQ

**Q: Can Ledgerly send per-print images to UAT?**  
A: Yes — lifestyle galleries via `colorImagesJson`. Swatches are store-only.

**Q: What happens if I re-import the xlsx?**  
A: Stock/prints/variants update; existing swatches and galleries are preserved when print names match (with alias map for renames like `BLUE` → `BABY BLUE`).

**Q: Where do print names come from if not in the spreadsheet cells?**  
A: Embedded swatch photos matched against the PRINT NAMES sheet (+ manual overrides).

**Q: How do swatch images get onto the website?**  
A: Either `prod-restore-print-swatch-images.py` (from PRINT NAMES sheet → R2 → `swatchUrl`) or manual upload in Admin → Products. Not from Ledgerly.

**Q: What's the difference between print name mapping and swatch URL restore?**  
A: Name mapping (image hash) tells you *which print* a slot is. Swatch restore uploads the master artwork file and sets `swatchUrl`. Run import first, then swatch restore.

**Q: Max images per print?**  
A: 10 lifestyle URLs per print (`ProductColorImagesSupport.MAX_IMAGES_PER_COLOR`).
