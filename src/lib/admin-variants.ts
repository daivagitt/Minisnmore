import { parseVariantsJson } from "@/lib/product-variants";

export type VariantEditorRow = {
  id?: string;
  sku: string;
  size: string;
  color: string;
  rate: number | "";
  cost: number | "";
  stock: number | "";
  active: boolean;
};

export function suggestVariantSku(parentSku: string, size: string, color: string): string {
  const base = parentSku.trim() || "SKU";
  const parts = [base];
  if (size.trim()) parts.push(size.trim().toUpperCase().replace(/\s+/g, "-"));
  if (color.trim()) parts.push(color.trim().toUpperCase().replace(/\s+/g, "-"));
  return parts.join("-");
}

export function rowsFromVariantsJson(raw: string | null | undefined): VariantEditorRow[] {
  if (typeof raw === "string" && raw.trim()) {
    try {
      let parsed: unknown = JSON.parse(raw.trim());
      if (typeof parsed === "string") parsed = JSON.parse(parsed);
      if (Array.isArray(parsed) && parsed.length) {
        return parsed.map((row) => {
          const r = (row ?? {}) as Record<string, unknown>;
          return {
            id: r.id != null ? String(r.id) : undefined,
            sku: String(r.sku ?? "").trim(),
            size: String(r.size ?? "").trim(),
            color: String(r.color ?? "").trim(),
            rate: r.rate != null && r.rate !== "" ? Number(r.rate) : "",
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
    return [{ sku: "", size: "", color: "", rate: "", cost: "", stock: 0, active: true }];
  }
  return parsed.map((v) => ({
    id: v.id,
    sku: v.sku,
    size: v.size ?? "",
    color: v.color ?? "",
    rate: v.compareAt ?? "",
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
  let minRate: number | null = null;
  let stock = 0;
  for (const r of rows) {
    if (!r.active) continue;
    if (r.size.trim() && !sizes.includes(r.size.trim())) sizes.push(r.size.trim());
    if (r.color.trim() && !colors.includes(r.color.trim())) colors.push(r.color.trim());
    const rate = Number(r.rate);
    if (Number.isFinite(rate) && rate > 0) {
      minRate = minRate == null ? rate : Math.min(minRate, rate);
    }
    stock += Math.max(0, Math.floor(Number(r.stock) || 0));
  }
  return {
    sizes,
    colors,
    compare_at_price: minRate,
    price: minRate != null ? Math.round(minRate * 0.8 * 100) / 100 : null,
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
    if (!(Number(r.rate) > 0)) errors.push(`Variant ${sku} needs a rate (selling price) > 0.`);
  }
  return errors;
}
