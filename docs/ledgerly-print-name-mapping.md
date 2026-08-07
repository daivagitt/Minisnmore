# Product-wise print name mapping — Ledgerly verification

**Purpose:** Cross-check that every Ledgerly item uses the **exact same print name strings** as the UAT store catalog, so `colors`, `variantsJson[].color`, and `colorImagesJson` keys stay aligned on sync/pull.

**Last updated:** 2026-08-04  
**Environment:** UAT (`uat.minisnmore.com`)  
**Source of truth for this table:** Live UAT catalog DB (not the raw xlsx alone — some products differ after Ledgerly sync and manual gallery fixes).

**Related docs:**
- [SKU-wise print & Ledgerly images](./sku-wise-print-and-ledgerly-images.md)
- [Swatch URL fallback & Excel → print names](./swatch-url-fallback.md)

---

## Quick rules for Ledgerly

1. **`colors`** field = comma-separated list of print names below (exact spelling, caps, punctuation).
2. **`colorImagesJson`** keys must match those names **character-for-character**.
3. Each **`variantsJson[].color`** must be one of those names.
4. Ledgerly does **not** resolve print names from Excel swatch photos — that happens on the **store** side only.
5. **`swatchUrl`** is store-only; Ledgerly never sends it.

### Example Ledgerly item fragment

```json
{
  "sku": "BODY-SUIT-HALF-SLEEVE",
  "ledgerlyItemId": "9685",
  "colors": "AQUA, BIRD, SNAIL",
  "colorImagesJson": {
    "AQUA": ["https://…/aqua-1.jpg", "https://…/aqua-2.jpg"],
    "BIRD": ["https://…/bird-1.jpg"],
    "SNAIL": ["https://…/snail-1.jpg", "https://…/snail-2.jpg"]
  },
  "variantsJson": [
    { "sku": "BODY-SUIT-HALF-SLEEVE-03-P1", "size": "0-3 M", "color": "AQUA", "stock": 24, "active": true },
    { "sku": "BODY-SUIT-HALF-SLEEVE-03-P2", "size": "0-3 M", "color": "BIRD", "stock": 44, "active": true }
  ]
}
```

---

## How print names are implemented (not in Ledgerly)

Print names are **not** entered as text in the SKU WISE PRINT spreadsheet. Each **Print-1 … Print-11** cell on the main sheet holds an **embedded swatch photo only**. The import pipeline resolves the human name by matching that photo to the **PRINT NAMES** sheet.

### End-to-end flow

```text
SKU WISE PRINT xlsx
  Sheet: Final updated count - 21-07-26  →  Print-1…11 photos + quantities per size row
  Sheet: PRINT NAMES                     →  Col C = swatch artwork, Col D = human name
  Sheet: WHC 21-7-26                     →  Side button T Shirt stock override only

        │
        ▼
Store Admin → SKU-wise Print Import  (or CLI)
  deploy/scripts/uat-sku-wise-print-import.py
        │
        ├─ build_print_name_map()
        │     • Extract embedded images from main sheet (drawing anchors)
        │     • MD5 byte match against PRINT NAMES sheet images
        │     • Fallback: perceptual hash (Hamming ≤ 10) if Excel re-compressed
        │     • Apply PRINT_NAME_MANUAL_OVERRIDES for unmatched slots
        │
        ├─ build_products()
        │     • Build colorsJson + variantsJson (size × print matrix)
        │     • WHC sheet for Side button T Shirt (short labels: PINK, SNAIL, …)
        │     • merge_preserve_media() on re-import — keeps swatches/galleries
        │
        └─ Upsert via Admin API → product.colors_json, product.variants_json

Optional (store only):
  deploy/scripts/uat-restore-print-swatch-images.py
    → PRINT NAMES artwork → R2 → colors_json[].swatchUrl

Ledgerly sync (pull / push / webhook):
  LedgerlyProductMergeService.mergeLedgerlyItem()
    → colors from Ledgerly text
    → colorImagesJson → color_images_json + merge into colors_json imageUrls
  LedgerlyProductMergeService.buildPushPayload()
    → pushes colors, variantsJson, colorImagesJson back to Ledgerly
```

### Key code & script locations

| Piece | Location |
|-------|----------|
| Excel → print names (image hash match) | `deploy/scripts/uat-sku-wise-print-import.py` → `build_print_name_map()` |
| Excel → variants/stock | same script → `build_products()` |
| Admin xlsx upload endpoint | `InternalAdminImportResource.java` → `/internal/v1/admin/import/sku-wise-print` |
| Admin UI upload button | `src/routes/admin.products.tsx` |
| Ledgerly pull merge | `LedgerlyProductMergeService.java` → `mergeLedgerlyItem()` |
| Ledgerly push payload | `LedgerlyProductMergeService.java` → `buildPushPayload()` |
| Per-print gallery import to R2 | `LedgerlyItemImageSync.java` (when `LEDGERLY_SYNC_PRODUCT_IMAGES=true`) |
| Swatch restore from PRINT NAMES | `deploy/scripts/uat-restore-print-swatch-images.py` |
| Storefront merge | `src/lib/product-colors.ts` → `mergeColorImagesIntoColors()` |

### Manual overrides (when image match fails)

Defined in `PRINT_NAME_MANUAL_OVERRIDES` in `uat-sku-wise-print-import.py`:

| Excel row | Print slot | Resolved name | Reason |
|-----------|------------|---------------|--------|
| 5 | 3 | `BABY BLUE` | Loop Back Fleece — no image on row, siblings agree |
| 72 | 3 | `AQUA` | Baby Front Open Frock 0-3M — qty but no swatch |
| 37–40 | 1 | `PINK & BEE` | Front Open Vest Half Sleeve — pink swatch, not aqua+blue |
| 118 | 5 | `COMBO 5` | Face Napkin — wrong auto-match |
| 118 | 7 | `COMBO 7` | Face Napkin — no image, sequential COMBO pattern |

### Side button short labels (WHC sheet)

**Side button T Shirt (Half sleeve)** and **Side button T Shirt & Shorts Set** take print names from the **WHC 21-7-26** sheet, not image matching:

| WHC / store label | Swatch artwork alias (restore script only) |
|-------------------|---------------------------------------------|
| `PINK` | → `pink buds` (for swatch image lookup) |
| `SNAIL` | (direct) |
| `BLUE` | → `baby blue` |
| `BEE` | → `happy bee` |
| `FISH` | → `aqua` |

**In Ledgerly, use the store labels** (`PINK`, `SNAIL`, `BLUE`, `BEE`, `FISH`) — not the PRINT NAMES artwork names.

---

## Product-wise mapping (UAT live — use in Ledgerly)

Verify each row: Ledgerly `colors` + `colorImagesJson` keys + every `variantsJson[].color` match **exactly**.

| # | Parent SKU | Product name | Ledgerly ID | Print names (exact strings) | Notes |
|---|------------|--------------|-------------|----------------------------|-------|
| 1 | `BABY-FRONT-OPEN-FROCK-HALF-SLEEVE` | Baby Front Open Frock (Half sleeve) | 9683 | `AQUA`, `BIRD`, `HAPPY BEE`, `PINK BUDS` | 4 prints |
| 2 | `BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS` | Baby Front Open Frock (Half sleeve) & Bloomers | 9684 | `HAPPY BEE`, `PINK BUDS`, `SNAIL` | 3 prints |
| 3 | `BABY-SWADDLE` | Baby Swaddle | 9693 | `BROWN SWADDLE`, `GREEN SWADDLE`, `WHITE SWADDLE` | 3 prints (Ledgerly trimmed from xlsx 5-print set) |
| 4 | `BABY-TERRY-TOWEL-FLUFFY` | Baby Terry Towel-Fluffy | 9694 | `BROWN WAVES`, `CORAL PINK`, `Ocean`, `VELVET DUSK` | `Ocean` — capital O only |
| 5 | `BASIC-SHORTS` | BASIC SHORTS | 9680 | `BABY BLUE`, `BEE & BLUE`, `HAPPY BEE`, `SNAIL` | 4 prints |
| 6 | `BIBS-SPECIAL` | Bibs Special | 9692 | `BEE & BLUE`, `SNAIL & BEE`, `SNAIL & BLUE` | 3 prints |
| 7 | `BODY-SUIT-HALF-SLEEVE` | Body Suit (Half Sleeve) | 9685 | `AQUA`, `BIRD`, `SNAIL` | 3 prints |
| 8 | `BOOK-STYLE-TYING-JABLA` | Book Style Tying Jabla | 9676 | `BLUE`, `FISH`, `BIRD` | Not `BABY BLUE` — store uses short `BLUE` |
| 9 | `BOOK-STYLE-TYING-JABLA-SHORT-SET` | Book Style Tying Jabla short set | 9677 | `BLUE`, `FISH`, `BIRD` | Same as jabla |
| 10 | `CAP-SOCKS-MITTEN-SET` | CAP SOCKS MITTEN SET | 9687 | `AQUA`, `SNAIL` | 2 prints |
| 11 | `FACE-NAPKIN-WASH-CLOTH-10-10` | Face Napkin/Wash Cloth (10*10) | 9691 | `COMBO 1` … `COMBO 10`, `Print 11` | ⚠️ Print 11 still generic — fix in Ledgerly |
| 12 | `FRONT-OPEN-VEST-HALF-SLEEVE` | Front Open Vest Half Sleeve | 9678 | `AQUA & AQUA`, `AQUA & BEE`, `PINK & AQUA` | Ampersands + spaces exact |
| 13 | `FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET` | Front Open Vest Half Sleeve & Short Set | 9679 | `AQUA`, `HAPPY BEE`, `SNAIL` | 3 prints |
| 14 | `FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET` | Front Open Vest Sleeveless & Short Set | 9675 | `AQUA`, `BABY BLUE`, `SNAIL` | 3 prints |
| 15 | `FRONT-OPEN-VEST-SLEVELESS` | Front Open Vest Sleveless | 9674 | `AQUA & BLUE`, `SNAIL & AQUA`, `SNAIL & BLUE` | Typo “Sleveless” in product name |
| 16 | `LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEV` | Loop Back Fleece knit Front Open Vest (Full sleeve) | 9695 | `AQUA`, `BABY BLUE`, `PINK BUDS` | ⚠️ Ledgerly SKU may be `…FULL-SLEEVE` — align SKU |
| 17 | `PACK-OF-3-MITTENS` | Pack Of 3 Mittens | 9688 | `SNAIL & BEE`, `SNAIL,BEE & AQUA` | Comma in second name — exact |
| 18 | `ROUND-CAP` | ROUND CAP | 9686 | `AQUA`, `BIRD` | 2 prints |
| 19 | `SIDE-BUTTON-T-SHIRT-HALF-SLEEVE` | Side button T Shirt (Half sleeve) | — | `PINK`, `SNAIL`, `BLUE`, `BEE`, `FISH` | ⚠️ Not linked to Ledgerly yet |
| 20 | `SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE` | Side button T Shirt & Shorts Set (Half sleeve) | 9681 | `PINK`, `SNAIL`, `BLUE`, `BEE`, `FISH` | WHC short labels |
| 21 | `SOCKS` | SOCKS | 9689 | `MULTI COLOR` | Single “print” |
| 22 | `TYING-CAP` | Tying Cap | 9690 | `RABBIT, BIRD & BLUE`, `TREE, BIRD & BLUE` | Commas inside name |

---

## Per-product Ledgerly `colors` field (copy-paste)

Use these verbatim in the Ledgerly item **colors** field:

```
BABY-FRONT-OPEN-FROCK-HALF-SLEEVE
  AQUA, BIRD, HAPPY BEE, PINK BUDS

BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS
  HAPPY BEE, PINK BUDS, SNAIL

BABY-SWADDLE
  BROWN SWADDLE, GREEN SWADDLE, WHITE SWADDLE

BABY-TERRY-TOWEL-FLUFFY
  BROWN WAVES, CORAL PINK, Ocean, VELVET DUSK

BASIC-SHORTS
  BABY BLUE, BEE & BLUE, HAPPY BEE, SNAIL

BIBS-SPECIAL
  BEE & BLUE, SNAIL & BEE, SNAIL & BLUE

BODY-SUIT-HALF-SLEEVE
  AQUA, BIRD, SNAIL

BOOK-STYLE-TYING-JABLA
  BLUE, FISH, BIRD

BOOK-STYLE-TYING-JABLA-SHORT-SET
  BLUE, FISH, BIRD

CAP-SOCKS-MITTEN-SET
  AQUA, SNAIL

FACE-NAPKIN-WASH-CLOTH-10-10
  COMBO 1, COMBO 2, COMBO 3, COMBO 4, COMBO 5, COMBO 6, COMBO 7, COMBO 8, COMBO 9, COMBO 10, Print 11

FRONT-OPEN-VEST-HALF-SLEEVE
  AQUA & AQUA, AQUA & BEE, PINK & AQUA

FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET
  AQUA, HAPPY BEE, SNAIL

FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET
  AQUA, BABY BLUE, SNAIL

FRONT-OPEN-VEST-SLEVELESS
  AQUA & BLUE, SNAIL & AQUA, SNAIL & BLUE

LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEV
  AQUA, BABY BLUE, PINK BUDS

PACK-OF-3-MITTENS
  SNAIL & BEE, SNAIL,BEE & AQUA

ROUND-CAP
  AQUA, BIRD

SIDE-BUTTON-T-SHIRT-HALF-SLEEVE
  PINK, SNAIL, BLUE, BEE, FISH

SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE
  PINK, SNAIL, BLUE, BEE, FISH

SOCKS
  MULTI COLOR

TYING-CAP
  RABBIT, BIRD & BLUE, TREE, BIRD & BLUE
```

---

## Per-product verification checklist

For each SKU in Ledgerly:

- [ ] **Ledgerly item ID** matches table (or item is linked after first sync)
- [ ] **`colors`** string lists every print name exactly
- [ ] **`colorImagesJson`** has a key for each print (same spelling)
- [ ] Each **`variantsJson[].color`** is one of those names
- [ ] Lifestyle URLs under each key are for that print only (not shared across all prints)
- [ ] **`imageUrl`** (listing hero) is set

### Sign-off block (copy per product)

```text
SKU: ___________________________
Ledgerly ID: ___________________
Verified by: __________________  Date: __________

Print names in Ledgerly colors:     [ ] Match table above
colorImagesJson keys:               [ ] Match
variantsJson colors:                [ ] Match
Per-print lifestyle images uploaded:[ ] Yes  [ ] N/A

Issues:
_________________________________________________________________
```

---

## Known gaps / drift to watch

| Issue | Products | Action in Ledgerly |
|-------|----------|-------------------|
| Not linked to Ledgerly | `SIDE-BUTTON-T-SHIRT-HALF-SLEEVE` | Create/link item; use print names above |
| SKU mismatch on inventory pull | Loop-back fleece (`…FULL-SLEEV` vs `…FULL-SLEEVE`) | Align parent SKU with store |
| Generic print name | Face Napkin `Print 11` | Rename to proper COMBO name when known |
| xlsx ≠ live UAT (after Ledgerly) | Baby Swaddle, Book Style Jabla, Front Open Vest Half Sleeve | **Trust this doc (live UAT)**, not raw xlsx slots |
| Swatch URLs | All | Store-only — re-run `uat-restore-print-swatch-images.py` after Ledgerly pull |

---

## Refreshing this document

Re-export from UAT:

```bash
PGPASSWORD='…' psql -h 127.0.0.1 -U minisandmore_user -d miniandmore -c "
SELECT sku, ledgerly_item_id, colors_json
FROM product WHERE active=true AND sku NOT LIKE 'TEST%'
ORDER BY sku;"
```

Re-run xlsx name resolution (dry-run only):

```bash
python3 deploy/scripts/uat-sku-wise-print-import.py --dry-run
```

---

## Sign-off

| Area | Verified | Date | Initials |
|------|----------|------|----------|
| All Ledgerly items have correct print names | [ ] | | |
| All colorImagesJson keys match | [ ] | | |
| Per-print images uploaded in Ledgerly | [ ] | | |
| Sync / pull tested on sample product | [ ] | | |
