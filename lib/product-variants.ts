export type ProductVariant = {
  id?: string;
  sku: string;
  size?: string;
  color?: string;
  /** Storefront unit price (after UAT discount when applied server-side). */
  price: number;
  /** Ledgerly rate / compare-at selling price. */
  compareAt?: number;
  stock: number;
  active: boolean;
};

export type VariantProductLike = {
  itemStructure?: string | null;
  variants?: ProductVariant[];
  sizes: string[];
  colors: { name: string; hex: string }[];
  price: number;
  compareAt?: number;
  sku?: string;
};

export function isVariantsProduct(p: VariantProductLike | null | undefined): boolean {
  return (p?.itemStructure ?? "").toUpperCase() === "VARIANTS";
}

export function hasSellableVariants(p: VariantProductLike | null | undefined): boolean {
  return isVariantsProduct(p) && (p?.variants?.some(isVariantSellable) ?? false);
}

export function parseVariantsJson(raw: unknown): ProductVariant[] {
  if (raw == null) return [];
  let parsed: unknown = raw;
  if (typeof raw === "string") {
    const trimmed = raw.trim();
    if (!trimmed) return [];
    try {
      parsed = JSON.parse(trimmed);
      if (typeof parsed === "string") parsed = JSON.parse(parsed);
    } catch {
      return [];
    }
  }
  if (!Array.isArray(parsed)) return [];
  return parsed
    .map((row): ProductVariant | null => {
      if (!row || typeof row !== "object") return null;
      const r = row as Record<string, unknown>;
      const sku = String(r.sku ?? "").trim();
      if (!sku) return null;
      const rate = Number(r.rate ?? r.compareAt ?? r.compare_at ?? 0) || 0;
      const priceRaw = r.price != null ? Number(r.price) : NaN;
      const price = Number.isFinite(priceRaw) && priceRaw > 0 ? priceRaw : Math.round(rate * 0.8 * 100) / 100;
      return {
        id: r.id != null ? String(r.id) : undefined,
        sku,
        size: r.size != null && String(r.size).trim() ? String(r.size).trim() : undefined,
        color: r.color != null && String(r.color).trim() ? String(r.color).trim() : undefined,
        price,
        compareAt: rate > 0 ? rate : undefined,
        stock: Math.max(0, Math.floor(Number(r.stock) || 0)),
        active: r.active === false || r.active === "false" || r.active === 0 ? false : true,
      };
    })
    .filter((v): v is ProductVariant => v != null);
}

function key(value: string | undefined | null): string {
  return (value ?? "").trim().toLowerCase();
}

export function findVariant(
  product: VariantProductLike,
  opts: { size?: string | null; color?: string | null; sku?: string | null },
): ProductVariant | undefined {
  const variants = product.variants ?? [];
  if (opts.sku) {
    const want = opts.sku.trim().toLowerCase();
    const bySku = variants.find((v) => v.sku.toLowerCase() === want);
    if (bySku) return bySku;
  }
  return variants.find((v) => key(v.size) === key(opts.size) && key(v.color) === key(opts.color));
}

export function colorsForSize(product: VariantProductLike, size: string | null | undefined): string[] {
  if (!isVariantsProduct(product)) {
    return product.colors.map((c) => c.name);
  }
  const want = key(size);
  const names = new Set<string>();
  for (const v of product.variants ?? []) {
    if (!v.active) continue;
    if (key(v.size) !== want) continue;
    if (v.color) names.add(v.color);
  }
  return Array.from(names);
}

export function isVariantSellable(variant: ProductVariant | undefined | null): boolean {
  return !!variant && variant.active && variant.stock > 0 && !!variant.sku;
}

export function lineUnitPrice(product: VariantProductLike, opts: { size?: string | null; color?: string | null; sku?: string | null }): number {
  if (isVariantsProduct(product)) {
    const v = findVariant(product, opts);
    return v?.price ?? product.price;
  }
  return product.price;
}

export function defaultSellableSelection(product: VariantProductLike): { size: string; color: string; variant?: ProductVariant } {
  if (!isVariantsProduct(product)) {
    return {
      size: product.sizes[0] ?? "One Size",
      color: product.colors[0]?.name ?? "Natural",
    };
  }
  const first = (product.variants ?? []).find(isVariantSellable) ?? (product.variants ?? [])[0];
  return {
    size: first?.size ?? product.sizes[0] ?? "One Size",
    color: first?.color ?? product.colors[0]?.name ?? "Natural",
    variant: first,
  };
}
