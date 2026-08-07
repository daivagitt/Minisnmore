# SKU-wise print mapping verification (Excel vs UAT)

**Generated:** 2026-08-05 08:08 UTC  
**Excel source:** `SKU WISE PRINT  FINAL DETAILS AS ON 25-07-26.xlsx`  
**UAT gateway:** `http://127.0.0.1:9180`  
**Script:** `deploy/scripts/generate-print-mapping-verification.py`

Use this file to confirm each **Print-N → print name** from the spreadsheet is present in the live catalog (`colorsJson` and `variantsJson[].color`).

Also see the reference mapping (Excel only): [sku-wise-print-slot-mapping.md](./sku-wise-print-slot-mapping.md)

## Summary

| Metric | Count |
|--------|------:|
| Products in Excel | 22 |
| SKU missing from catalog | 0 |
| Print slots ✅ OK | 59 |
| Print slots ⚠️/❌ | 8 |

---

## Baby Front Open Frock (Half sleeve)

**Parent SKU:** `BABY-FRONT-OPEN-FROCK-HALF-SLEEVE`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | BIRD | yes | yes | ✅ OK |
| Print-2 | HAPPY BEE | yes | yes | ✅ OK |
| Print-3 | AQUA | yes | yes | ✅ OK |
| Print-4 | PINK BUDS | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA, BIRD, HAPPY BEE, PINK BUDS

**Import script would set:** AQUA, BIRD, HAPPY BEE, PINK BUDS
**Import `colorsJson` ↔ live catalog:** ✅ match

## Baby Front Open Frock (Half sleeve) & Bloomers

**Parent SKU:** `BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | SNAIL | yes | yes | ✅ OK |
| Print-2 | PINK BUDS | yes | yes | ✅ OK |
| Print-3 | HAPPY BEE | yes | yes | ✅ OK |

**UAT `colorsJson`:** HAPPY BEE, PINK BUDS, SNAIL

**Import script would set:** HAPPY BEE, PINK BUDS, SNAIL
**Import `colorsJson` ↔ live catalog:** ✅ match

## Baby Swaddle

**Parent SKU:** `BABY-SWADDLE`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** brown swaddle, green swaddle, white swaddle
- **Missing in catalog (in Excel slots):** bird

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | BIRD | no | no | ❌ missing |

**UAT `colorsJson`:** BROWN SWADDLE, GREEN SWADDLE, WHITE SWADDLE

**Import script would set:** BIRD, Print 2, Print 3, Print 4, Print 5
**Import `colorsJson` ↔ live catalog:** ❌ differ

## Baby Terry Towel-Fluffy

**Parent SKU:** `BABY-TERRY-TOWEL-FLUFFY`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** ocean

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | VELVET DUSK | yes | yes | ✅ OK |
| Print-2 | BROWN WAVES | yes | yes | ✅ OK |
| Print-4 | CORAL PINK | yes | yes | ✅ OK |

**UAT `colorsJson`:** BROWN WAVES, CORAL PINK, Ocean, VELVET DUSK

**Import script would set:** BROWN WAVES, CORAL PINK, Print 3, VELVET DUSK
**Import `colorsJson` ↔ live catalog:** ❌ differ

## BASIC SHORTS

**Parent SKU:** `BASIC-SHORTS`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** snail
- **Missing in catalog (in Excel slots):** snail & blue

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | SNAIL & BLUE | no | no | ❌ missing |
| Print-2 | HAPPY BEE | yes | yes | ✅ OK |
| Print-3 | BEE & BLUE | yes | yes | ✅ OK |
| Print-4 | BABY BLUE | yes | yes | ✅ OK |

**UAT `colorsJson`:** BABY BLUE, BEE & BLUE, HAPPY BEE, SNAIL

**Import script would set:** BABY BLUE, BEE & BLUE, HAPPY BEE, SNAIL, SNAIL & BEE, SNAIL & BLUE
**Import `colorsJson` ↔ live catalog:** ❌ differ

## Bibs Special

**Parent SKU:** `BIBS-SPECIAL`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | SNAIL & BEE | yes | yes | ✅ OK |
| Print-2 | SNAIL & BLUE | yes | yes | ✅ OK |
| Print-3 | BEE & BLUE | yes | yes | ✅ OK |

**UAT `colorsJson`:** BEE & BLUE, SNAIL & BEE, SNAIL & BLUE

**Import script would set:** BEE & BLUE, SNAIL & BEE, SNAIL & BLUE
**Import `colorsJson` ↔ live catalog:** ✅ match

## Body Suit (Half Sleeve)

**Parent SKU:** `BODY-SUIT-HALF-SLEEVE`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | SNAIL | yes | yes | ✅ OK |
| Print-2 | AQUA | yes | yes | ✅ OK |
| Print-3 | BIRD | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA, BIRD, SNAIL

**Import script would set:** AQUA, BIRD, SNAIL
**Import `colorsJson` ↔ live catalog:** ✅ match

## Book Style Tying Jabla

**Parent SKU:** `BOOK-STYLE-TYING-JABLA`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** blue, fish
- **Missing in catalog (in Excel slots):** aqua, baby blue

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | BABY BLUE | no | no | ❌ missing |
| Print-2 | BIRD | yes | yes | ✅ OK |
| Print-3 | AQUA | no | no | ❌ missing |

**UAT `colorsJson`:** BLUE, FISH, BIRD

**Import script would set:** AQUA, BABY BLUE, BIRD
**Import `colorsJson` ↔ live catalog:** ❌ differ

## Book Style Tying Jabla short set

**Parent SKU:** `BOOK-STYLE-TYING-JABLA-SHORT-SET`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** bird, blue, fish
- **Missing in catalog (in Excel slots):** baby blue

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | BABY BLUE | no | no | ❌ missing |

**UAT `colorsJson`:** BLUE, FISH, BIRD

**Import script would set:** BABY BLUE
**Import `colorsJson` ↔ live catalog:** ❌ differ

## CAP SOCKS MITTEN SET

**Parent SKU:** `CAP-SOCKS-MITTEN-SET`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | SNAIL | yes | yes | ✅ OK |
| Print-2 | AQUA | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA, SNAIL

**Import script would set:** AQUA, SNAIL
**Import `colorsJson` ↔ live catalog:** ✅ match

## Face Napkin/Wash Cloth (10*10)

**Parent SKU:** `FACE-NAPKIN-WASH-CLOTH-10-10`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** print 11

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | COMBO 1 | yes | yes | ✅ OK |
| Print-2 | COMBO 2 | yes | yes | ✅ OK |
| Print-3 | COMBO 3 | yes | yes | ✅ OK |
| Print-4 | COMBO 4 | yes | yes | ✅ OK |
| Print-5 | COMBO 5 | yes | yes | ✅ OK |
| Print-6 | COMBO 6 | yes | yes | ✅ OK |
| Print-7 | COMBO 7 | yes | yes | ✅ OK |
| Print-8 | COMBO 8 | yes | yes | ✅ OK |
| Print-9 | COMBO 9 | yes | yes | ✅ OK |
| Print-10 | COMBO 10 | yes | yes | ✅ OK |

**UAT `colorsJson`:** COMBO 1, COMBO 2, COMBO 3, COMBO 4, COMBO 5, COMBO 6, COMBO 7, COMBO 8, COMBO 9, COMBO 10, Print 11

**Import script would set:** COMBO 1, COMBO 10, COMBO 2, COMBO 3, COMBO 4, COMBO 5, COMBO 6, COMBO 7, COMBO 8, COMBO 9
**Import `colorsJson` ↔ live catalog:** ❌ differ

## Front Open Vest Half Sleeve

**Parent SKU:** `FRONT-OPEN-VEST-HALF-SLEEVE`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** aqua & aqua
- **Missing in catalog (in Excel slots):** pink & bee

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | PINK & BEE | no | no | ❌ missing |
| Print-2 | AQUA & BEE | yes | yes | ✅ OK |
| Print-3 | PINK & AQUA | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA & AQUA, AQUA & BEE, PINK & AQUA

**Import script would set:** AQUA & AQUA , AQUA & BEE, PINK & AQUA, PINK & BEE
**Import `colorsJson` ↔ live catalog:** ❌ differ

## Front Open Vest Half Sleeve & Short Set

**Parent SKU:** `FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | HAPPY BEE | yes | yes | ✅ OK |
| Print-2 | AQUA | yes | yes | ✅ OK |
| Print-3 | SNAIL | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA, HAPPY BEE, SNAIL

**Import script would set:** AQUA, HAPPY BEE, SNAIL
**Import `colorsJson` ↔ live catalog:** ✅ match

## Front Open Vest Sleeveless & Short Set

**Parent SKU:** `FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | AQUA | yes | yes | ✅ OK |
| Print-2 | BABY BLUE | yes | yes | ✅ OK |
| Print-3 | SNAIL | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA, BABY BLUE, SNAIL

**Import script would set:** AQUA, BABY BLUE, SNAIL
**Import `colorsJson` ↔ live catalog:** ✅ match

## Front Open Vest Sleveless

**Parent SKU:** `FRONT-OPEN-VEST-SLEVELESS`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | SNAIL & AQUA | yes | yes | ✅ OK |
| Print-2 | SNAIL & BLUE | yes | yes | ✅ OK |
| Print-3 | AQUA & BLUE | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA & BLUE, SNAIL & AQUA, SNAIL & BLUE

**Import script would set:** AQUA & BLUE, SNAIL & AQUA, SNAIL & BLUE
**Import `colorsJson` ↔ live catalog:** ✅ match

## Loop Back Fleece knit Front Open Vest (Full sleeve)

**Parent SKU:** `LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEV`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | AQUA | yes | yes | ✅ OK |
| Print-2 | PINK BUDS | yes | yes | ✅ OK |
| Print-3 | BABY BLUE | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA, BABY BLUE, PINK BUDS

**Import script would set:** AQUA, BABY BLUE, PINK BUDS
**Import `colorsJson` ↔ live catalog:** ✅ match

## Pack Of 3 Mittens

**Parent SKU:** `PACK-OF-3-MITTENS`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | SNAIL & BEE | yes | yes | ✅ OK |
| Print-2 | SNAIL,BEE & AQUA | yes | yes | ✅ OK |

**UAT `colorsJson`:** SNAIL & BEE, SNAIL,BEE & AQUA

**Import script would set:** SNAIL & BEE, SNAIL,BEE & AQUA
**Import `colorsJson` ↔ live catalog:** ✅ match

## ROUND CAP

**Parent SKU:** `ROUND-CAP`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | BIRD | yes | yes | ✅ OK |
| Print-2 | AQUA | yes | yes | ✅ OK |

**UAT `colorsJson`:** AQUA, BIRD

**Import script would set:** AQUA, BIRD
**Import `colorsJson` ↔ live catalog:** ✅ match

## Side button T Shirt  (Half sleeve)

**Parent SKU:** `SIDE-BUTTON-T-SHIRT-HALF-SLEEVE`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | PINK | yes | yes | ✅ OK |
| Print-2 | SNAIL | yes | yes | ✅ OK |
| Print-3 | BLUE | yes | yes | ✅ OK |
| Print-4 | BEE | yes | yes | ✅ OK |
| Print-5 | FISH | yes | yes | ✅ OK |

**UAT `colorsJson`:** PINK, SNAIL, BLUE, BEE, FISH

**Import script would set:** BEE, BLUE, FISH, PINK, SNAIL
**Import `colorsJson` ↔ live catalog:** ✅ match

## Side button T Shirt & Shorts Set  (Half sleeve)

**Parent SKU:** `SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE`  
**Set match (Excel slots ↔ catalog colors):** ❌
- **Extra in catalog (not in Excel slots):** bee, blue, fish, pink
- **Missing in catalog (in Excel slots):** aqua, happy bee

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | AQUA | no | no | ❌ missing |
| Print-2 | SNAIL | yes | yes | ✅ OK |
| Print-3 | HAPPY BEE | no | no | ❌ missing |

**UAT `colorsJson`:** PINK, SNAIL, BLUE, BEE, FISH

**Import script would set:** AQUA, HAPPY BEE, SNAIL
**Import `colorsJson` ↔ live catalog:** ❌ differ

## SOCKS

**Parent SKU:** `SOCKS`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | MULTI COLOR | yes | yes | ✅ OK |

**UAT `colorsJson`:** MULTI COLOR

**Import script would set:** MULTI COLOR
**Import `colorsJson` ↔ live catalog:** ✅ match

## Tying Cap

**Parent SKU:** `TYING-CAP`  
**Set match (Excel slots ↔ catalog colors):** ✅

| Print slot | Excel expected | In `colorsJson` | In variants | Result |
|------------|----------------|-----------------|-------------|--------|
| Print-1 | RABBIT, BIRD & BLUE | yes | yes | ✅ OK |
| Print-2 | TREE, BIRD & BLUE | yes | yes | ✅ OK |

**UAT `colorsJson`:** RABBIT, BIRD & BLUE, TREE, BIRD & BLUE

**Import script would set:** RABBIT, BIRD & BLUE, TREE, BIRD & BLUE
**Import `colorsJson` ↔ live catalog:** ✅ match
