import { parseVariantsJson } from "@/lib/product-variants";

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
};

function variantAmount(value: number | string | ""): number {
  if (value === "" || value == null) return NaN;
  const n = Number(value);
  return Number.isFinite(n) ? n : NaN;
}

export function variantAmountDisplay(value: number | string | ""): string {
  if (value === "" || value == null) return "";
  return String(value);
}

/** Keep raw string while typing (e.g. "799.", "0.") — parse on blur. */
export function variantAmountInput(raw: string): number | string | "" {
  const v = raw.trim();
  if (!v) return "";
  if (!/^\d*\.?\d*$/.test(v)) return "";
  if (v.endsWith(".") || v === ".") return v;
  const n = Number(v);
  return Number.isFinite(n) ? n : "";
}

export function variantAmountBlur(value: number | string | ""): number | "" {
  if (value === "" || value == null) return "";
  const n = Number(value);
  return Number.isFinite(n) ? n : "";
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

export function rowsFromVariantsJson(raw: string | null | undefined): VariantEditorRow[] {
  if (typeof raw === "string" && raw.trim()) {
    try {
      let parsed: unknown = JSON.parse(raw.trim());
      if (typeof parsed === "string") parsed = JSON.parse(parsed);
      if (Array.isArray(parsed) && parsed.length) {
        return parsed.map((row) => {
          const r = (row ?? {}) as Record<string, unknown>;
          const rateRaw = r.rate != null && r.rate !== "" ? Number(r.rate) : NaN;
          const mrpRaw =
            r.mrp != null && r.mrp !== ""
              ? Number(r.mrp)
              : r.compareAt != null && r.compareAt !== ""
                ? Number(r.compareAt)
                : NaN;
          // Legacy rows often stored MRP in `rate` only — split for the new columns.
          const hasExplicitMrp = Number.isFinite(mrpRaw) && mrpRaw > 0;
          const hasRate = Number.isFinite(rateRaw) && rateRaw > 0;
          let rate: number | "" = hasRate ? rateRaw : "";
          let mrp: number | "" = hasExplicitMrp ? mrpRaw : "";
          if (hasRate && !hasExplicitMrp) {
            // Treat legacy rate as MRP; derive selling at 80%.
            mrp = rateRaw;
            rate = Math.round(rateRaw * 0.8 * 100) / 100;
          }
          return {
            id: r.id != null ? String(r.id) : undefined,
            sku: String(r.sku ?? "").trim(),
            size: String(r.size ?? "").trim(),
            color: String(r.color ?? "").trim(),
            rate,
            mrp,
            cost: r.cost != null && r.cost !== "" ? Number(r.cost) : "",
            stock: r.stock != null && r.stock !== "" ? Number(r.stock) : 0,
            active: r.active === false || r.active === "false" || r.active === 0 ? false : true,
          };
        });
      }
    } catch {
      // fall through
    }
  }
  const parsed = parseVariantsJson(raw);
  if (!parsed.length) {
    return [{ sku: "", size: "", color: "", rate: "", mrp: "", cost: "", stock: 0, active: true }];
  }
  return parsed.map((v) => ({
    id: v.id,
    sku: v.sku,
    size: v.size ?? "",
    color: v.color ?? "",
    rate: v.price > 0 ? v.price : "",
    mrp: v.compareAt ?? "",
    cost: "",
    stock: v.stock,
    active: v.active,
  }));
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
      if (Number(r.mrp) > 0) out.mrp = Number(r.mrp);
      if (r.id) out.id = r.id;
      if (r.size.trim()) out.size = r.size.trim();
      if (r.color.trim()) out.color = r.color.trim();
      return out;
    });
  return JSON.stringify(payload);
}

export function aggregateFromVariantRows(rows: VariantEditorRow[]): {
  sizes: string[];
  colors: string[];
  compare_at_price: number | null;
  price: number | null;
  stock: number;
} {
  const sizes: string[] = [];
  const colors: string[] = [];
  let minSelling: number | null = null;
  let minMrp: number | null = null;
  let stock = 0;
  for (const r of rows) {
    // Sizes/colors include inactive rows so the storefront can render them disabled instead of hiding them.
    if (r.size.trim() && !sizes.includes(r.size.trim())) sizes.push(r.size.trim());
    if (r.color.trim() && !colors.includes(r.color.trim())) colors.push(r.color.trim());
    if (!r.active) continue;
    const selling = variantAmount(r.rate);
    if (Number.isFinite(selling) && selling > 0) {
      minSelling = minSelling == null ? selling : Math.min(minSelling, selling);
    }
    const mrp = variantAmount(r.mrp);
    if (Number.isFinite(mrp) && mrp > 0) {
      minMrp = minMrp == null ? mrp : Math.min(minMrp, mrp);
    }
    stock += Math.max(0, Math.floor(Number(r.stock) || 0));
  }
  return {
    sizes,
    colors,
    compare_at_price: minMrp ?? (minSelling != null ? Math.round((minSelling / 0.8) * 100) / 100 : null),
    price: minSelling,
    stock,
  };
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
