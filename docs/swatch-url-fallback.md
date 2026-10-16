# Swatch URL fallback & Excel → print name display

**Audience:** Anyone wondering what happens when prints have **no separate swatch URLs**, and how the **SKU WISE PRINT** spreadsheet turns silent swatch photos into the print names you see on the storefront.

**Last updated:** 2026-08-04

**Related:** [SKU-wise print mapping, image uploads & Ledgerly](./sku-wise-print-and-ledgerly-images.md) · [PDP gallery & LazyImage](./pdp-gallery-and-lazy-images.md)

---

## TL;DR

| Question | Answer |
|----------|--------|
| No separate `swatchUrl`? | Colour picker uses that print’s **first lifestyle photo**, then a **hex colour**. Listing cards prefer lifestyle galleries; if none exist, they use **one** swatch. |
| How do print names appear from Excel? | Main sheet Print columns are **images only** (no text). Import matches those photos to the **PRINT NAMES** sheet by image hash → writes the human name into `colorsJson` / variants. Swatch **image URLs** are a separate restore step. |

Ledgerly never supplies swatch URLs. Names and swatch artwork both ultimately come from the spreadsheet (or admin upload).

---

## Table of contents

1. [If you don’t give separate swatch URLs](#1-if-you-dont-give-separate-swatch-urls)
2. [How Excel maps print swatches → print names](#2-how-excel-maps-print-swatches--print-names)
3. [How those names + swatches show on the site](#3-how-those-names--swatches-show-on-the-site)
4. [Optional: restore dedicated `swatchUrl`s from Excel](#4-optional-restore-dedicated-swatchurls-from-excel)
5. [Re-import: keep media when names change](#5-re-import-keep-media-when-names-change)
6. [What does *not* fall back](#6-what-does-not-fall-back)
7. [Troubleshooting](#7-troubleshooting)
8. [Code references](#8-code-references)

---

## 1. If you don’t give separate swatch URLs

You do **not** have to set `swatchUrl` on every print. The storefront still shows something useful.

### Colour picker (PDP / Quick View)

`swatchImageForColor()` in `frontend/src/lib/product-colors.ts` picks the first non-empty value:

```
swatchUrl  →  imageUrl  →  imageUrls[0]  →  (no image → solid hex background)
```

| Priority | Field | When it applies |
|----------|-------|-----------------|
| 1 | `swatchUrl` | Dedicated print artwork (restore script or admin upload) |
| 2 | `imageUrl` | Single lifestyle photo on that print |
| 3 | `imageUrls[0]` | First lifestyle photo when the print has a gallery |
| 4 | *(none)* | Button uses `hexForColorName(name)` — a flat colour, not fabric |

So if you never uploaded swatches but Ledgerly (or R2 restore) filled lifestyle galleries, the picker thumbnails are simply the **first gallery shot per print**.

The print **label** next to/under the button is always `colorsJson[].name` (e.g. `PINK & BEE`) — that does not depend on having a swatch URL.

### Listing / shop cards (`images_json`)

`collectStorefrontCardImages()`:

1. Collect every print’s lifestyle URLs (in print order).
2. If any exist → use those (deduped) for the card.
3. Else → use the **first** print that has a `swatchUrl` (**one** swatch only, not every print).
4. Else → empty.

Same rule on the backend when saving with an empty `images_json`: lifestyle first, then one swatch (`ProductColorImagesSupport.ensureProductImagesFromColorGalleries`).

### PDP main carousel — swatch is never used here

`galleryForSelectedColor()` shows **lifestyle** photos only.

| Selected print has… | Main gallery |
|---------------------|--------------|
| Lifestyle URLs | Those photos |
| **Only** `swatchUrl` | **Empty** carousel (by design) |
| Neither | Empty / product-level fallback if not a per-print product |

Swatches belong in the **print picker**, not the hero strip.

### Mental model

```text
You didn’t set swatchUrl
        │
        ▼
Has this print a lifestyle photo?
   yes → picker shows that photo; listing can use galleries
   no  → picker shows hex; listing may stay blank until a swatch or hero is set
```

There is **no** automatic placeholder CDN URL. Missing media → lifestyle → hex / empty.

---

## 2. How Excel maps print swatches → print names

**File:** `SKU WISE PRINT  FINAL DETAILS AS ON 25-07-26.xlsx` (repo root)  
**Script:** `deploy/scripts/prod-sku-wise-print-import.py`

Print **names** and print **swatch image URLs** are two different stages.

### Spreadsheet sheets

| Sheet | Role |
|-------|------|
| `Final updated count - 21-07-26` | Styles, sizes, quantities. **Print-1…Print-11** cells hold **embedded swatch photos only** — no print-name text. |
| `PRINT NAMES` | Master catalogue: one row per print = **image (col C)** + **human name (col D)** (e.g. `PINK & BEE`, `BABY BLUE`). |
| `WHC 21-7-26` | Print/stock override for *Side button T Shirt* only. |

### Stage A — Resolve names (no URL upload)

```text
Main sheet Print-N photo          PRINT NAMES sheet
(silent image in cell)     ↔     Col C image + Col D name
         │                              │
         └──── image match ─────────────┘
                      │
                      ▼
            "PINK & BEE" written into
            colorsJson + variantsJson[].color
```

Matching (`build_print_name_map`):

1. Index PRINT NAMES images by **MD5**, plus an **8×8 perceptual hash**.
2. For each main-sheet Print-N photo: exact MD5 → else closest pHash with Hamming distance ≤ 10.
3. Apply **`PRINT_NAME_MANUAL_OVERRIDES`** for known mismatches (always wins), e.g. Front Open Vest Half Sleeve Print-1 → `PINK & BEE`.

Result per product: a list of display names like `["PINK & BEE", "AQUA & BEE", "BABY BLUE"]`.  
Variant SKUs use the same string in `color` (e.g. `…-PINK-BEE`).

Import **does not** set `swatchUrl`. It only sets/refreshes names, stock, and the size×print matrix, and **preserves** any existing media (see §5).

### Stage B — Optional dedicated swatch URLs

See [§4](#4-optional-restore-dedicated-swatchurls-from-excel). Until you run that (or upload in admin), §1 fallback applies.

### End-to-end from Excel

```text
┌─────────────────────────────────────────────────────────────────┐
│  SKU WISE PRINT xlsx                                            │
│  Main sheet Print-1…11 photos ──match──▶ PRINT NAMES (C+D)      │
└────────────────────────┬────────────────────────┬───────────────┘
                         │                        │
                         ▼                        ▼
              uat-sku-wise-print-import   uat-restore-print-swatch-images
                         │                        │
                         ▼                        ▼
              colorsJson names            colorsJson[].swatchUrl
              variantsJson.color          (R2 …-swatch.jpg)
                         │                        │
                         └──────────┬─────────────┘
                                    ▼
                         Storefront: name label + picker image
```

---

## 3. How those names + swatches show on the site

| UI | What it shows | Source |
|----|---------------|--------|
| Print picker **label** | Human print name | `colorsJson[].name` (from Excel match / Ledgerly colour list) |
| Print picker **thumbnail** | Fabric swatch or fallback | `swatchImageForColor` chain (§1) |
| PDP **carousel** | Lifestyle photos for selected print | `imageUrl` / `imageUrls` / `colorImagesJson` — **not** `swatchUrl` |
| Shop **card hero** | One listing image | `images_json`, backfilled from galleries then one swatch |

`normalizeStorefrontColors()` keeps swatch and gallery separate: if `swatchUrl` is set, that URL is **removed** from the lifestyle list so the same file is not double-counted as a gallery photo.

API read merges Ledgerly `colorImagesJson` into colours via `mergeColorImagesIntoColors()` — that adds **galleries only**, never a `swatchUrl`.

---

## 4. Optional: restore dedicated `swatchUrl`s from Excel

**Script:** `deploy/scripts/prod-restore-print-swatch-images.py`

After names exist on products:

1. Read PRINT NAMES → `norm_key(name)` → image bytes (`norm_key` lowercases / collapses spaces).
2. Upload each needed print as JPEG to R2 (`…-PINK-BEE-swatch.jpg`).
3. For each product colour, set `swatchUrl`:
   - exact `norm_key(print name)` match, else
   - **`SWATCH_NAME_ALIASES`** (short matrix label → full PRINT NAMES artwork):

| Product / matrix name | Alias → PRINT NAMES artwork |
|-----------------------|-----------------------------|
| `pink` | `pink buds` |
| `blue` | `baby blue` |
| `bee` | `happy bee` |
| `fish` | `aqua` |

4. **Does not** overwrite lifestyle `imageUrl` / `imageUrls`.

```bash
set -a && source /etc/mini-and-more/prod.env && set +a
GATEWAY_URL=http://127.0.0.1:9180 ADMIN_USER=admin ADMIN_PASS=admin6 \
  python3 deploy/scripts/prod-restore-print-swatch-images.py
```

Admin → Products → colour swatch upload also sets `swatchUrl` directly (correct when PRINT NAMES has a name but no image in col C).

---

## 5. Re-import: keep media when names change

`prod-sku-wise-print-import.py` → `merge_preserve_media()` copies `swatchUrl`, `hex`, and galleries from the previous DB row onto the new print name:

```
1. exact norm_key(new name) in old colors_json
2. COLOR_MEDIA_ALIASES[new] → try old names in order
3. no match → plain name only (no media)
```

Example aliases: `baby blue` ← also search `blue`; `pink & bee` ← also search `aqua & blue`.

Non-empty listing `images_json` is kept across re-import when already set.

---

## 6. What does *not* fall back

| Scenario | Result |
|----------|--------|
| No `swatchUrl` and no lifestyle URLs | Hex picker button; listing may have no hero |
| Swatch-only print on PDP | Empty main gallery (swatch stays in picker only) |
| Ledgerly pull | Galleries may update; **swatches are not sent** — re-run restore or admin upload after sync |
| PRINT NAMES row with name but empty col C | Restore cannot set `swatchUrl` for that name — admin upload or manual override |
| Print renamed without a `COLOR_MEDIA_ALIASES` entry | Old media may not follow the new name until restore / alias |

---

## 7. Troubleshooting

| Symptom | Likely cause | Fix |
|---------|--------------|-----|
| Picker is a flat beige/tan square | No `swatchUrl` and no lifestyle URLs | Run swatch restore or upload lifestyle/swatch in admin |
| Wrong print **name** on a SKU | Image match / override wrong | Fix `PRINT_NAME_MANUAL_OVERRIDES` or PRINT NAMES sheet; re-run import |
| Name OK, wrong/missing **swatch image** | Restore not run, or alias/name mismatch | `prod-restore-print-swatch-images.py` or admin upload |
| BLUE shows “baby blue” artwork | Expected via `SWATCH_NAME_ALIASES` | Rename print in sheet or change alias map |
| Swatch in picker, empty PDP photos | Swatch-only print | Upload lifestyle gallery for that print |
| Swatches gone after Ledgerly sync | Ledgerly does not carry `swatchUrl` | Re-run swatch restore |

---

## 8. Code references

| Concern | Location |
|---------|----------|
| Picker thumbnail chain | `frontend/src/lib/product-colors.ts` → `swatchImageForColor()` |
| Listing hero collection | `frontend/src/lib/product-colors.ts` → `collectStorefrontCardImages()` |
| PDP gallery (no swatch) | `frontend/src/lib/product-gallery.ts` → `galleryForSelectedColor()` |
| Print picker UI | `frontend/src/components/VariantPickers.tsx` |
| Excel → print names | `deploy/scripts/prod-sku-wise-print-import.py` → `build_print_name_map()` |
| Excel → `swatchUrl` | `deploy/scripts/prod-restore-print-swatch-images.py` |
| Backend listing backfill | `ProductColorImagesSupport.ensureProductImagesFromColorGalleries()` |
| Tests | `frontend/src/lib/product-colors.merge.test.ts` |

### Typical UAT order

```bash
# 1. Names + stock + variants from xlsx (preserves existing media)
python3 deploy/scripts/prod-sku-wise-print-import.py

# 2. Dedicated swatch URLs from PRINT NAMES sheet → R2 → DB
python3 deploy/scripts/prod-restore-print-swatch-images.py
```
