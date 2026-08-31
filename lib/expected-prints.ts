import {
  genericPrintSlotIndex,
  isGenericPrintSlotName,
  type ProductColorEntry,
} from "@/lib/product-colors";

function colorKey(name: string): string {
  return name.trim().toLowerCase();
}

/**
 * UAT / Ledgerly display print names per parent SKU (Print-1 → index 0).
 * Used when the API still stores generic Print-N slot labels in colors_json.
 */
export const EXPECTED_PRINTS_BY_SKU: Record<string, readonly string[]> = {
  "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE": ["BIRD", "HAPPY BEE", "PINK BUDS", "AQUA"],
  "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS": ["HAPPY BEE", "PINK BUDS", "SNAIL"],
  "BABY-SWADDLE": ["BROWN SWADDLE", "GREEN SWADDLE", "WHITE SWADDLE"],
  "BABY-TERRY-TOWEL-FLUFFY": ["BROWN WAVES", "CORAL PINK", "Ocean", "VELVET DUSK"],
  "BASIC-SHORTS": ["BABY BLUE", "BEE & BLUE", "HAPPY BEE", "SNAIL"],
  "BIBS-SPECIAL": ["SNAIL & BEE", "SNAIL & BLUE", "BEE & BLUE"],
  "BODY-SUIT-HALF-SLEEVE": ["SNAIL", "AQUA", "BIRD"],
  "BOOK-STYLE-TYING-JABLA": ["BLUE", "BIRD", "FISH"],
  "BOOK-STYLE-TYING-JABLA-SHORT-SET": ["BLUE", "BIRD", "FISH"],
  "CAP-SOCKS-MITTEN-SET": ["SNAIL", "AQUA"],
  "FACE-NAPKIN-WASH-CLOTH-10-10": [
    "COMBO 1",
    "COMBO 2",
    "COMBO 3",
    "COMBO 4",
    "COMBO 5",
    "COMBO 6",
    "COMBO 7",
    "COMBO 8",
    "COMBO 9",
    "COMBO 10",
    "COMBO 11",
  ],
  "FRONT-OPEN-VEST-HALF-SLEEVE": ["AQUA & AQUA", "AQUA & BEE", "PINK & AQUA"],
  "FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET": ["AQUA", "HAPPY BEE", "SNAIL"],
  "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET": ["AQUA", "BABY BLUE", "SNAIL"],
  "FRONT-OPEN-VEST-SLEVELESS": ["SNAIL & AQUA", "SNAIL & BLUE", "AQUA & BLUE"],
  "LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEVE": ["AQUA", "PINK BUDS", "BABY BLUE"],
  "PACK-OF-3-MITTENS": ["SNAIL & BEE", "SNAIL,BEE & AQUA"],
  "ROUND-CAP": ["BIRD", "AQUA"],
  "SIDE-BUTTON-T-SHIRT-HALF-SLEEVE": ["PINK", "SNAIL", "BLUE", "BEE", "FISH"],
  "SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE": ["PINK", "SNAIL", "BLUE", "BEE", "FISH"],
  "SOCKS": ["MULTI COLOR"],
  "TYING-CAP": ["RABBIT, BIRD & BLUE", "TREE, BIRD & BLUE"],
};

export function expectedPrintNameForSlot(
  parentSku: string | null | undefined,
  slotIndex: number,
): string | undefined {
  const sku = parentSku?.trim().toUpperCase();
  if (!sku) return undefined;
  const list = EXPECTED_PRINTS_BY_SKU[sku];
  if (!list?.length || slotIndex < 0 || slotIndex >= list.length) return undefined;
  const name = list[slotIndex]?.trim();
  return name && !isGenericPrintSlotName(name) ? name : undefined;
}

export function resolveExpectedPrintLabel(
  parentSku: string | null | undefined,
  colorName: string | null | undefined,
): string | undefined {
  if (!colorName?.trim() || !isGenericPrintSlotName(colorName)) return undefined;
  const slotIdx = genericPrintSlotIndex(colorName);
  if (slotIdx == null) return undefined;
  return expectedPrintNameForSlot(parentSku, slotIdx);
}

/** Rename Print-N colour rows to catalog display names when the parent SKU is known. */
export function applyExpectedPrintNamesToColors(
  entries: ProductColorEntry[],
  parentSku: string | null | undefined,
): ProductColorEntry[] {
  const sku = parentSku?.trim().toUpperCase();
  if (!sku || !EXPECTED_PRINTS_BY_SKU[sku]?.length) return entries;

  return entries.map((entry, i) => {
    if (!isGenericPrintSlotName(entry.name)) return entry;
    const slotIdx = genericPrintSlotIndex(entry.name) ?? i;
    const newName = expectedPrintNameForSlot(sku, slotIdx);
    return newName ? { ...entry, name: newName } : entry;
  });
}

/** Rewrite variant matrix colours from Print-N to display names (JSON string in/out). */
export function applyExpectedPrintNamesToVariantsJson(
  variantsJson: string | null | undefined,
  parentSku: string | null | undefined,
): string | null {
  if (!variantsJson?.trim()) return variantsJson ?? null;
  const sku = parentSku?.trim().toUpperCase();
  if (!sku || !EXPECTED_PRINTS_BY_SKU[sku]?.length) return variantsJson;

  let parsed: unknown;
  try {
    parsed = JSON.parse(variantsJson);
    if (typeof parsed === "string") parsed = JSON.parse(parsed);
  } catch {
    return variantsJson;
  }
  if (!Array.isArray(parsed)) return variantsJson;

  const next = parsed.map((row) => {
    if (!row || typeof row !== "object") return row;
    const r = row as Record<string, unknown>;
    const color = String(r.color ?? "").trim();
    if (!color || !isGenericPrintSlotName(color)) return row;
    const slotIdx = genericPrintSlotIndex(color);
    const newName = slotIdx != null ? expectedPrintNameForSlot(sku, slotIdx) : undefined;
    return newName ? { ...r, color: newName } : row;
  });

  return JSON.stringify(next);
}

/** Customer-facing print label — uses catalog colours, then SKU slot table, then positional fallback. */
export function resolveStorefrontColorLabel(
  colors: { name: string }[] | undefined,
  colorName: string | null | undefined,
  parentSku?: string | null,
): string {
  if (!colorName?.trim()) return "";
  const trimmed = colorName.trim();
  const list = colors ?? [];

  if (isGenericPrintSlotName(trimmed)) {
    const fromSku = resolveExpectedPrintLabel(parentSku, trimmed);
    if (fromSku) return fromSku;
  }

  const exact = list.find((c) => colorKey(c.name) === colorKey(trimmed));
  if (exact && !isGenericPrintSlotName(exact.name)) return exact.name;

  const slotIdx = genericPrintSlotIndex(trimmed);
  if (slotIdx != null && slotIdx < list.length) {
    const bySlot = list[slotIdx];
    if (bySlot && !isGenericPrintSlotName(bySlot.name)) return bySlot.name;
  }
  return trimmed;
}
