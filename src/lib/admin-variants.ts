import { sortSizesAsc } from "@/lib/product-ages";
import { parseVariantsJson } from "@/lib/product-variants";
import { DEFAULT_STORE_DISCOUNT_PERCENT, sellingFromMrp } from "@/lib/pricing";

export type VariantEditorRow = {
  id?: string;
  sku: string;
  size: string;
  color: string;
  /** Selling price → Ledgerly variant rate */
  rate: number | string | "";
  /** MRP / list → Ledgerly variant mrp */
  mrp: number | string | "";
  cost: number | string | "";
  stock: number | string | "";
  active: boolean;
  /** Zoho Books item_id for this variant SKU (read-only in admin; set by sync/pull). */
  zohoItemId?: string;
  /** Lifestyle photo for this size×colour SKU (Zoho item image). */
  imageUrl?: string;
  /** Extra photos for this SKU (rear, lifestyle) — preserved on admin save. */
  imageUrls?: string[];
};

export type ZohoVariantLinkageSummary = {
  activeRows: number;
  linkedRows: number;
  /** Human label for parent product sync strip, e.g. "8/10 variant items in Zoho". */
  label: string;
};

/** Count variant rows with a cached Zoho item id (VARIANTS parents have no single parent Zoho item). */
export function summarizeZohoVariantLinkage(variantsJson: string | null | undefined): ZohoVariantLinkageSummary {
  const rows = rowsFromVariantsJson(variantsJson);
  const activeRows = rows.filter((r) => r.active && r.sku.trim()).length;
  const linkedRows = rows.filter((r) => r.active && r.sku.trim() && r.zohoItemId?.trim()).length;
  const label =
    activeRows === 0
      ? "no active variant SKUs"
      : linkedRows === 0
        ? `${activeRows} variant SKU(s) — not linked in Zoho yet`
        : linkedRows === activeRows
          ? `${linkedRows} variant item(s) in Zoho`
          : `${linkedRows}/${activeRows} variant item(s) in Zoho`;
  return { activeRows, linkedRows, label };
}

export function isVariantsItemStructure(itemStructure: string | null | undefined): boolean {
  return (itemStructure ?? "").trim().toUpperCase() === "VARIANTS";
}

/** Digits-only Zoho item-group id, or empty if the value is not a group id. */
export function digitsOnlyZohoId(value: string | null | undefined): string {
  return (value ?? "").replace(/\D/g, "");
}

/**
 * Parent {@code zoho_item_id} is a Zoho item-group id only when it is digits and is not itself
 * one of the variant item ids (a SIMPLE→VARIANTS conversion can leave a child item id there).
 */
export function zohoItemGroupId(product: {
  zoho_item_id?: string | null;
  item_structure?: string | null;
  variants_json?: string | null;
}): string | null {
  if (!isVariantsItemStructure(product.item_structure)) return null;
  const parentId = product.zoho_item_id?.trim() || "";
  if (!/^\d+$/.test(parentId)) return null;
  const childIds = new Set(
    rowsFromVariantsJson(product.variants_json)
      .map((row) => row.zohoItemId?.trim())
      .filter((id): id is string => Boolean(id)),
  );
  if (childIds.has(parentId)) return null;
  return parentId;
}

export function variantAmount(value: number | string | ""): number {
  if (value === "" || value == null) return NaN;
  const n = Number(value);
  return Number.isFinite(n) ? n : NaN;
}

export function variantAmountDisplay(value: number | string | ""): string {
  if (value === "" || value == null) return "";
  return String(value);
}

/** Keep raw string while typing (e.g. "799.", "2.0", "2.67") — parse on blur. */
export function variantAmountInput(raw: string): number | string | "" {
  const v = raw.trim();
  if (!v) return "";
  if (!/^\d*\.?\d*$/.test(v)) return "";
  // Preserve drafts with a decimal point so controlled inputs can accept "2.", "2.0", "2.67".
  if (v.includes(".")) return v;
  const n = Number(v);
  return Number.isFinite(n) ? n : "";
}

export function variantAmountBlur(value: number | string | ""): number | "" {
  if (value === "" || value == null) return "";
  const n = Number(value);
  return Number.isFinite(n) ? n : "";
}

/** Storefront/admin selling price for a variant row — derived from MRP when MRP is set. */
export function variantRowSelling(
  row: Pick<VariantEditorRow, "rate" | "mrp">,
  discountPercent = DEFAULT_STORE_DISCOUNT_PERCENT,
): number | "" {
  const mrp = variantAmount(row.mrp);
  if (Number.isFinite(mrp) && mrp > 0) {
    return sellingFromMrp(mrp, discountPercent);
  }
  const rate = variantAmount(row.rate);
  return Number.isFinite(rate) && rate > 0 ? rate : "";
}

export function syncVariantRowSellingFromMrp(
  row: VariantEditorRow,
  discountPercent = DEFAULT_STORE_DISCOUNT_PERCENT,
): VariantEditorRow {
  const selling = variantRowSelling(row, discountPercent);
  return selling === "" ? row : { ...row, rate: selling };
}

export function suggestVariantSku(parentSku: string, size: string, color: string): string {
  const base = parentSku.trim() || "SKU";
  const parts = [base];
  if (size.trim()) parts.push(size.trim().toUpperCase().replace(/\s+/g, "-"));
  if (color.trim()) parts.push(color.trim().toUpperCase().replace(/\s+/g, "-"));
  return parts.join("-");
}

/** Parent SKU from product name — Ledgerly parity (e.g. "Baby Swaddle" → BABY-SWADDLE). */
export function suggestParentSku(name: string): string {
  const fromName = name
    .trim()
    .toUpperCase()
    .replace(/[^A-Z0-9]+/g, "-")
    .replace(/-+/g, "-")
    .replace(/^-|-$/g, "");
  if (fromName) return fromName.slice(0, 48);
  return `SKU-${Math.random().toString(36).slice(2, 7).toUpperCase()}`;
}

function variantComboKey(size: string, color: string): string {
  return `${size.trim().toLowerCase()}|${color.trim().toLowerCase()}`;
}

/**
 * Build size × colour rows. Existing combos keep row data (stock, prices, active).
 * New combos get suggested SKUs and default prices copied from any existing row.
 */
export function buildVariantMatrixFromSizesColors(
  sizes: string[],
  colors: string[],
  parentSku: string,
  existing: VariantEditorRow[] = [],
): VariantEditorRow[] {
  const cleanSizes = sizes.map((s) => s.trim()).filter(Boolean);
  const cleanColors = colors.map((c) => c.trim()).filter(Boolean);
  if (!cleanSizes.length || !cleanColors.length) return existing;

  const byCombo = new Map(existing.map((r) => [variantComboKey(r.size, r.color), r]));
  const template = existing.find((r) => variantAmount(r.rate) > 0 || variantAmount(r.mrp) > 0);
  const defaultRate = template && variantAmount(template.rate) > 0 ? template.rate : "";
  const defaultMrp = template && variantAmount(template.mrp) > 0 ? template.mrp : "";
  const defaultCost = template && variantAmount(template.cost) > 0 ? template.cost : "";

  const rows: VariantEditorRow[] = [];
  for (const size of cleanSizes) {
    for (const color of cleanColors) {
      const prev = byCombo.get(variantComboKey(size, color));
      if (prev) {
        rows.push({ ...prev, size, color, sku: prev.sku.trim() || suggestVariantSku(parentSku, size, color) });
      } else {
        rows.push({
          sku: suggestVariantSku(parentSku, size, color),
          size,
          color,
          rate: defaultRate,
          mrp: defaultMrp,
          cost: defaultCost,
          stock: 0,
          active: true,
        });
      }
    }
  }
  return rows;
}

/** Re-apply PARENT-SIZE-COLOR SKUs from current parent SKU (keeps other row fields). */
export function regenerateVariantSkus(rows: VariantEditorRow[], parentSku: string): VariantEditorRow[] {
  return rows.map((r) => ({
    ...r,
    sku: suggestVariantSku(parentSku, r.size, r.color),
  }));
}

export function rowsFromVariantsJson(raw: string | null | undefined, discountPercent = 20): VariantEditorRow[] {
  if (typeof raw === "string" && raw.trim()) {
    try {
      let parsed: unknown = JSON.parse(raw.trim());
      if (typeof parsed === "string") parsed = JSON.parse(parsed);
      if (Array.isArray(parsed) && parsed.length) {
        return parsed.map((row) => {
          const r = (row ?? {}) as Record<string, unknown>;
          const rateRaw = r.rate != null && r.rate !== "" ? Number(r.rate) : NaN;
          const hasMrpField = Object.prototype.hasOwnProperty.call(r, "mrp") || Object.prototype.hasOwnProperty.call(r, "compareAt");
          const mrpRaw =
            r.mrp != null && r.mrp !== ""
              ? Number(r.mrp)
              : r.compareAt != null && r.compareAt !== ""
                ? Number(r.compareAt)
                : r.mrp === 0 || r.mrp === "0" || r.compareAt === 0 || r.compareAt === "0"
                  ? 0
                  : NaN;
          // Legacy rows often stored MRP in `rate` only — split for the new columns.
          // If `mrp` was explicitly saved (even 0/null), do not re-interpret rate as MRP.
          const hasExplicitMrp = Number.isFinite(mrpRaw) && mrpRaw > 0;
          const hasRate = Number.isFinite(rateRaw) && rateRaw > 0;
          let rate: number | "" = hasRate ? rateRaw : "";
          let mrp: number | "" = hasExplicitMrp ? mrpRaw : Number.isFinite(mrpRaw) && mrpRaw === 0 ? 0 : "";
          if (hasRate && !hasMrpField) {
            mrp = rateRaw;
            rate = "";
          }
          const zohoRaw = r.zohoItemId ?? r.zoho_item_id;
          const imageRaw = r.imageUrl ?? r.image_url;
          const extraImages = Array.isArray(r.imageUrls)
            ? r.imageUrls.map((u) => String(u).trim()).filter(Boolean)
            : Array.isArray(r.image_urls)
              ? r.image_urls.map((u) => String(u).trim()).filter(Boolean)
              : [];
          return syncVariantRowSellingFromMrp(
            {
              id: r.id != null ? String(r.id) : undefined,
              sku: String(r.sku ?? "").trim(),
              size: String(r.size ?? "").trim(),
              color: String(r.color ?? "").trim(),
              rate,
              mrp,
              cost: r.cost != null && r.cost !== "" ? Number(r.cost) : "",
              stock: r.stock != null && r.stock !== "" ? Number(r.stock) : 0,
              active: r.active === false || r.active === "false" || r.active === 0 ? false : true,
              zohoItemId: zohoRaw != null && String(zohoRaw).trim() ? String(zohoRaw).trim() : undefined,
              imageUrl: imageRaw != null && String(imageRaw).trim() ? String(imageRaw).trim() : undefined,
              imageUrls: extraImages.length ? extraImages : undefined,
            },
            discountPercent,
          );
        });
      }
    } catch {
      // fall through
    }
  }
  const parsed = parseVariantsJson(raw, discountPercent);
  if (!parsed.length) {
    return [{ sku: "", size: "", color: "", rate: "", mrp: "", cost: "", stock: 0, active: true }];
  }
  return parsed.map((v) =>
    syncVariantRowSellingFromMrp(
      {
        id: v.id,
        sku: v.sku,
        size: v.size ?? "",
        color: v.color ?? "",
        rate: v.price > 0 ? v.price : "",
        mrp: v.compareAt ?? "",
        cost: "",
        stock: v.stock,
        active: v.active,
        imageUrl: v.imageUrl,
        imageUrls: v.imageUrls,
      },
      discountPercent,
    ),
  );
}

export function serializeVariantRows(rows: VariantEditorRow[]): string {
  const payload = rows
    .filter((r) => r.sku.trim() || r.size.trim() || r.color.trim())
    .map((r) => {
      const out: Record<string, unknown> = {
        sku: r.sku.trim() || suggestVariantSku("SKU", r.size, r.color),
        rate: Number(r.rate) || 0,
        cost: Number(r.cost) || 0,
        stock: Math.max(0, Math.floor(Number(r.stock) || 0)),
        active: !!r.active,
      };
      // Always persist mrp (0/null when cleared) so reload does not treat rate as legacy MRP.
      if (r.mrp === "" || r.mrp == null) {
        out.mrp = null;
      } else {
        const mrpNum = Number(r.mrp);
        if (Number.isFinite(mrpNum) && mrpNum > 0) out.mrp = mrpNum;
        else if (mrpNum === 0) out.mrp = 0;
        else out.mrp = null;
      }
      if (r.id) out.id = r.id;
      if (r.size.trim()) out.size = r.size.trim();
      if (r.color.trim()) out.color = r.color.trim();
      if (r.zohoItemId?.trim()) out.zohoItemId = r.zohoItemId.trim();
      if (r.imageUrl?.trim()) out.imageUrl = r.imageUrl.trim();
      if (r.imageUrls?.length) out.imageUrls = r.imageUrls.map((u) => u.trim()).filter(Boolean);
      return out;
    });
  return JSON.stringify(payload);
}

export function aggregateFromVariantRows(
  rows: VariantEditorRow[],
  catalogSizes: string[] = [],
  discountPercent = 20,
): {
  sizes: string[];
  colors: string[];
  compare_at_price: number | null;
  price: number | null;
  stock: number;
} {
  const sizeSet = new Set<string>();
  for (const size of catalogSizes) {
    const trimmed = size.trim();
    if (trimmed) sizeSet.add(trimmed);
  }
  const colors: string[] = [];
  let minSelling: number | null = null;
  let minMrp: number | null = null;
  let stock = 0;
  for (const r of rows) {
    // Sizes/colors include inactive rows so the storefront can render them disabled instead of hiding them.
    if (r.size.trim()) sizeSet.add(r.size.trim());
    if (r.color.trim() && !colors.includes(r.color.trim())) colors.push(r.color.trim());
    if (!r.active) continue;
    const selling = variantRowSelling(r, discountPercent);
    const mrp = variantAmount(r.mrp);
    if (Number.isFinite(selling) && selling > 0) {
      minSelling = minSelling == null ? selling : Math.min(minSelling, selling);
    }
    if (Number.isFinite(mrp) && mrp > 0) {
      minMrp = minMrp == null ? mrp : Math.min(minMrp, mrp);
    }
    stock += Math.max(0, Math.floor(Number(r.stock) || 0));
  }
  return {
    sizes: sortSizesAsc([...sizeSet]),
    colors,
    // Only surface real MRP — do not invent compare-at from selling when MRP was cleared to 0.
    compare_at_price: minMrp,
    price: minSelling,
    stock,
  };
}

type AdminProductPricingLike = {
  item_structure?: string | null;
  variants_json?: string | null;
  sizes?: string[];
  price?: number | null;
  compare_at_price?: number | null;
};

/** Align admin product price fields with MRP + global store discount (storefront parity). */
export function normalizeAdminProductPricing<T extends AdminProductPricingLike>(
  product: T,
  discountPercent = DEFAULT_STORE_DISCOUNT_PERCENT,
): T {
  if (isVariantsItemStructure(product.item_structure) && product.variants_json) {
    const rows = rowsFromVariantsJson(product.variants_json, discountPercent);
    const agg = aggregateFromVariantRows(rows, product.sizes ?? [], discountPercent);
    return {
      ...product,
      variants_json: serializeVariantRows(rows),
      price: agg.price,
      compare_at_price: agg.compare_at_price,
    };
  }
  const mrp = product.compare_at_price;
  if (mrp != null && Number(mrp) > 0) {
    return {
      ...product,
      price: sellingFromMrp(Number(mrp), discountPercent),
      compare_at_price: Number(mrp),
    };
  }
  return product;
}

export function validateVariantRows(raw: string | null | undefined): string[] {
  const errors: string[] = [];
  let rows: VariantEditorRow[];
  try {
    rows = rowsFromVariantsJson(raw);
  } catch {
    return ["Variants matrix JSON is invalid."];
  }
  const usable = rows.filter((r) => r.sku.trim() || r.size.trim() || r.color.trim());
  if (!usable.length) {
    errors.push("Add at least one variant row for VARIANTS products.");
    return errors;
  }
  const skus = new Set<string>();
  for (const r of usable) {
    const sku = (r.sku.trim() || suggestVariantSku("SKU", r.size, r.color)).toLowerCase();
    if (!sku) errors.push("Each variant needs a SKU.");
    if (skus.has(sku)) errors.push(`Duplicate variant SKU: ${sku}`);
    skus.add(sku);
    if (!(variantAmount(r.rate) > 0)) errors.push(`Variant ${sku} needs a selling price > 0.`);
  }
  return errors;
}
