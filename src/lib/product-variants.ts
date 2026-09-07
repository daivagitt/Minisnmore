import type { AgeRange } from "@/data/products";
import { normalizeSizeKey, sizeToAgeBand, sortSizesAsc } from "@/lib/product-ages";
import {
  DEFAULT_STORE_DISCOUNT_PERCENT,
  sellingFromMrp,
} from "@/lib/pricing";

const STANDARD_FOUR_AGE_BANDS: AgeRange[] = ["0-3m", "3-6m", "6-12m", "12-18m"];
const UNIVERSAL_SIZE_KEYS = new Set(["common", "commonsize", "freesize", "onesize", "os", "free", "universal"]);

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
  /** Lifestyle photo for this size×colour SKU (Zoho item image). */
  imageUrl?: string;
  imageUrls?: string[];
};

export type VariantProductLike = {
  itemStructure?: string | null;
  variants?: ProductVariant[];
  sizes: string[];
  colors: { name: string; hex: string; imageUrl?: string; imageUrls?: string[] }[];
  price: number;
  compareAt?: number;
  sku?: string;
  /** Stock for non-variant ("SIMPLE") products. Null/undefined means untracked — treated as always sellable. */
  stock?: number | null;
};

export function isVariantsProduct(p: VariantProductLike | null | undefined): boolean {
  const structure = (p?.itemStructure ?? "").toUpperCase();
  if (structure === "VARIANTS") return true;
  if (structure === "SIMPLE") return false;
  return (p?.variants?.length ?? 0) > 0;
}

export function hasSellableVariants(p: VariantProductLike | null | undefined): boolean {
  return isVariantsProduct(p) && (p?.variants?.some(isVariantSellable) ?? false);
}

export function parseVariantsJson(
  raw: unknown,
  discountPercent = DEFAULT_STORE_DISCOUNT_PERCENT,
): ProductVariant[] {
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
      const hasMrpField =
        Object.prototype.hasOwnProperty.call(r, "mrp") || Object.prototype.hasOwnProperty.call(r, "compareAt");
      const mrpRaw =
        r.mrp != null && r.mrp !== ""
          ? Number(r.mrp)
          : r.compareAt != null && r.compareAt !== ""
            ? Number(r.compareAt)
            : r.mrp === 0 || r.compareAt === 0
              ? 0
              : NaN;
      const priceRaw = r.price != null ? Number(r.price) : NaN;
      const hasExplicitMrp = Number.isFinite(mrpRaw) && mrpRaw > 0;
      const hasExplicitPrice = Number.isFinite(priceRaw) && priceRaw > 0;
      /** Legacy rows stored Ledgerly/list rate in `rate` with no separate mrp — treat rate as MRP. */
      const legacyRateIsMrp = rate > 0 && !hasExplicitMrp && !hasExplicitPrice && !hasMrpField;

      let price: number;
      let compareAt: number | undefined;
      if (legacyRateIsMrp) {
        compareAt = rate;
        price = sellingFromMrp(rate, discountPercent);
      } else if (hasExplicitMrp) {
        compareAt = mrpRaw;
        price = sellingFromMrp(mrpRaw, discountPercent);
      } else if (hasExplicitPrice) {
        price = priceRaw;
        compareAt = undefined;
      } else if (rate > 0) {
        // Modern row with mrp cleared (0/null): rate is the selling price.
        price = rate;
        compareAt = undefined;
      } else {
        price = 0;
        compareAt = undefined;
      }
      const imageUrl =
        r.imageUrl != null && String(r.imageUrl).trim()
          ? String(r.imageUrl).trim()
          : r.image_url != null && String(r.image_url).trim()
            ? String(r.image_url).trim()
            : undefined;
      const imageUrls = Array.isArray(r.imageUrls)
        ? r.imageUrls.map((u) => String(u).trim()).filter(Boolean)
        : Array.isArray(r.image_urls)
          ? r.image_urls.map((u) => String(u).trim()).filter(Boolean)
          : undefined;
      return {
        id: r.id != null ? String(r.id) : undefined,
        sku,
        size: r.size != null && String(r.size).trim() ? String(r.size).trim() : undefined,
        color: r.color != null && String(r.color).trim() ? String(r.color).trim() : undefined,
        price,
        compareAt,
        stock: Math.max(0, Math.floor(Number(r.stock) || 0)),
        active: r.active === false || r.active === "false" || r.active === 0 ? false : true,
        imageUrl,
        imageUrls,
      };
    })
    .filter((v): v is ProductVariant => v != null);
}

function sizeKey(value: string | undefined | null): string {
  return normalizeSizeKey(value ?? "");
}

function colorKey(value: string | undefined | null): string {
  return (value ?? "").trim().toLowerCase();
}

/** All size labels to render — merges catalog sizes, variant matrix, and standard baby bands. */
export function sizesForDisplay(product: VariantProductLike): string[] {
  const labelByKey = new Map<string, string>();
  const remember = (label: string | undefined | null) => {
    const trimmed = label?.trim();
    if (!trimmed) return;
    const k = sizeKey(trimmed);
    if (!labelByKey.has(k)) labelByKey.set(k, trimmed);
  };

  for (const s of product.sizes ?? []) remember(s);
  for (const v of product.variants ?? []) remember(v.size);

  const labels = [...labelByKey.values()];
  const hasAgeBand = labels.some((s) => sizeToAgeBand(s) != null);
  const isUniversal = labels.some((s) => UNIVERSAL_SIZE_KEYS.has(sizeKey(s)));

  if (isVariantsProduct(product) && hasAgeBand && !isUniversal) {
    const usesSpacedM = labels.some((s) => /\d\s*-\s*\d+\s+m$/i.test(s.trim()));
    for (const band of STANDARD_FOUR_AGE_BANDS) {
      if (labelByKey.has(sizeKey(band))) continue;
      remember(usesSpacedM ? band.replace(/m$/i, " M") : band);
    }
  }

  return sortSizesAsc([...labelByKey.values()]);
}

/** Sizes on the catalog / variant matrix only — no standard-band padding for the size picker. */
export function catalogSizesForProduct(product: VariantProductLike): string[] {
  const labelByKey = new Map<string, string>();
  const remember = (label: string | undefined | null) => {
    const trimmed = label?.trim();
    if (!trimmed) return;
    const k = sizeKey(trimmed);
    if (!labelByKey.has(k)) labelByKey.set(k, trimmed);
  };

  for (const s of product.sizes ?? []) remember(s);
  for (const v of product.variants ?? []) remember(v.size);

  if (labelByKey.size === 0) remember("One Size");
  return sortSizesAsc([...labelByKey.values()]);
}

/** In-stock catalog sizes for the size calculator (not the size-chart table). */
export function availableSizesForFit(product: VariantProductLike): string[] {
  const catalog = catalogSizesForProduct(product);
  if (!isVariantsProduct(product)) return catalog;
  return catalog.filter((s) => sizeHasSellableVariants(product, s));
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
  return variants.find((v) => sizeKey(v.size) === sizeKey(opts.size) && colorKey(v.color) === colorKey(opts.color));
}

export function colorsForSize(product: VariantProductLike, size: string | null | undefined): string[] {
  if (!isVariantsProduct(product)) {
    return product.colors.map((c) => c.name);
  }
  const want = sizeKey(size);
  const names = new Set<string>();
  for (const v of product.variants ?? []) {
    if (!v.active) continue;
    if (sizeKey(v.size) !== want) continue;
    if (v.color) names.add(v.color);
  }
  return Array.from(names);
}

/** In-stock colours for a size (used for default selection). */
export function sellableColorsForSize(product: VariantProductLike, size: string | null | undefined): string[] {
  if (!isVariantsProduct(product)) {
    return product.colors.map((c) => c.name);
  }
  const want = sizeKey(size);
  const names = new Set<string>();
  for (const v of product.variants ?? []) {
    if (!isVariantSellable(v)) continue;
    if (sizeKey(v.size) !== want) continue;
    if (v.color) names.add(v.color);
  }
  return Array.from(names);
}

/** Case-insensitive colour name match for variant matrix ↔ colours_json alignment. */
export function colorNameInList(colorName: string, names: string[]): boolean {
  const want = colorKey(colorName);
  return names.some((n) => colorKey(n) === want);
}

export function isVariantSellable(variant: ProductVariant | undefined | null): boolean {
  return !!variant && variant.active && variant.stock > 0 && !!variant.sku;
}

/** True for non-variant ("SIMPLE") products whose tracked stock (if any) isn't exhausted. */
export function isSimpleProductSellable(product: VariantProductLike | null | undefined): boolean {
  if (!product) return false;
  return product.stock == null || product.stock > 0;
}

/** True if at least one active in-stock variant exists for this size. */
export function sizeHasSellableVariants(product: VariantProductLike, size: string | null | undefined): boolean {
  if (!isVariantsProduct(product)) return isSimpleProductSellable(product);
  const want = sizeKey(size);
  return (product.variants ?? []).some((v) => v.active && v.stock > 0 && sizeKey(v.size) === want);
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

/** MRP / compare-at for a cart or order line — falls back to selling price when no MRP is set. */
export function lineUnitMrp(
  product: VariantProductLike,
  opts: { size?: string | null; color?: string | null; sku?: string | null },
): number {
  const unitPrice = lineUnitPrice(product, opts);
  if (isVariantsProduct(product)) {
    const v = findVariant(product, opts);
    const compareAt = v?.compareAt ?? product.compareAt;
    if (compareAt != null && compareAt > unitPrice) return compareAt;
  } else if (product.compareAt != null && product.compareAt > unitPrice) {
    return product.compareAt;
  }
  return unitPrice;
}

/**
 * Card/list price for a product — matches the PDP default selection for VARIANTS
 * (not the parent min-of-matrix price, which can be skewed by bad variant rows).
 */
export function storefrontListPricing(product: VariantProductLike): { price: number; compareAt?: number } {
  if (isVariantsProduct(product) && (product.variants?.length ?? 0) > 0) {
    const { variant } = defaultSellableSelection(product);
    if (variant && variant.price > 0) {
      const compareAt =
        variant.compareAt != null && variant.compareAt > variant.price ? variant.compareAt : undefined;
      return { price: variant.price, compareAt };
    }
  }
  const price = Number(product.price) || 0;
  const compareAt =
    product.compareAt != null && product.compareAt > price ? product.compareAt : undefined;
  return { price, compareAt };
}

export function defaultSellableSelection(product: VariantProductLike): { size: string; color: string; variant?: ProductVariant } {
  const sizes = sizesForDisplay(product);
  if (!isVariantsProduct(product)) {
    return {
      size: sizes[0] ?? "One Size",
      color: product.colors[0]?.name ?? "Natural",
    };
  }
  for (const size of sizes) {
    if (!sizeHasSellableVariants(product, size)) continue;
    for (const colorName of sellableColorsForSize(product, size)) {
      const variant = findVariant(product, { size, color: colorName });
      if (isVariantSellable(variant)) {
        return { size, color: colorName, variant };
      }
    }
  }
  const first = (product.variants ?? []).find(isVariantSellable) ?? (product.variants ?? [])[0];
  return {
    size: first?.size ?? sizes[0] ?? "One Size",
    color: first?.color ?? product.colors[0]?.name ?? "Natural",
    variant: first,
  };
}
