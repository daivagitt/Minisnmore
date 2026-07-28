export type ProductVariant = {
  id?: string;
  sku: string;
  size?: string;
  color?: string;
  /** Storefront unit price (after UAT discount when applied server-side). */
  price: number;
  /** Ledgerly rate / MRP (compare-at). Storefront selling price is typically 80% of this. */
  compareAt?: number;
  stock: number;
  active: boolean;
};

export type VariantProductLike = {
  itemStructure?: string | null;
  variants?: ProductVariant[];
  sizes: string[];
  colors: { name: string; hex: string; imageUrl?: string; imageUrls?: string[] }[];
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
      const rate = Number(r.rate ?? 0) || 0;
      const mrpRaw = r.mrp != null ? Number(r.mrp) : r.compareAt != null ? Number(r.compareAt) : NaN;
      const priceRaw = r.price != null ? Number(r.price) : NaN;
      const hasExplicitMrp = Number.isFinite(mrpRaw) && mrpRaw > 0;
      const hasExplicitPrice = Number.isFinite(priceRaw) && priceRaw > 0;
      /** Legacy rows stored Ledgerly/list rate in `rate` with no separate mrp — treat rate as MRP. */
      const legacyRateIsMrp =
        rate > 0 && !hasExplicitMrp && !hasExplicitPrice && r.mrp == null && r.compareAt == null;

      let price: number;
      let compareAt: number | undefined;
      if (legacyRateIsMrp) {
        compareAt = rate;
        price = Math.round(rate * 0.8 * 100) / 100;
      } else {
        const hasSelling = rate > 0;
        price =
          hasExplicitPrice
            ? priceRaw
            : hasSelling
              ? rate
              : hasExplicitMrp
                ? Math.round(mrpRaw * 0.8 * 100) / 100
                : 0;
        compareAt = hasExplicitMrp ? mrpRaw : hasSelling ? Math.round(rate / 0.8 * 100) / 100 : undefined;
      }
      return {
        id: r.id != null ? String(r.id) : undefined,
        sku,
        size: r.size != null && String(r.size).trim() ? String(r.size).trim() : undefined,
        color: r.color != null && String(r.color).trim() ? String(r.color).trim() : undefined,
        price,
        compareAt,
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

/** True if at least one active in-stock variant exists for this size. */
export function sizeHasSellableVariants(product: VariantProductLike, size: string | null | undefined): boolean {
  if (!isVariantsProduct(product)) return true;
  const want = key(size);
  return (product.variants ?? []).some((v) => v.active && v.stock > 0 && key(v.size) === want);
}

/** Human-readable stock message for the selected size × colour. */
export function variantStockMessage(
  product: VariantProductLike,
  variant: ProductVariant | undefined,
  opts: { size?: string | null; color?: string | null },
): string | null {
  if (!isVariantsProduct(product)) return null;
  if (!opts.size || !opts.color) return "Choose a size and colour to check availability.";
  if (!variant) return "This size and colour combination is not available.";
  if (!variant.active) return "This variant is no longer available.";
  if (variant.stock <= 0) return "Sold out — this size and colour is out of stock.";
  if (variant.stock <= 3) return `Only ${variant.stock} left in this size and colour.`;
  return `${variant.stock} in stock for this size and colour.`;
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
