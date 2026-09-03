import type { Product } from "@/data/products";
import type { CartItem } from "@/store/shop";
import { galleryForSelectedColor } from "@/lib/product-gallery";
import { resolveStorefrontColorLabel } from "@/lib/expected-prints";
import { FIT_LIMITS } from "@/lib/product-fit";
import { roundInr } from "@/lib/money";
import {
  findVariant,
  isSimpleProductSellable,
  isVariantSellable,
  isVariantsProduct,
  lineUnitMrp,
  lineUnitPrice,
} from "@/lib/product-variants";

export type CartSyncResult = {
  cart: CartItem[];
  removed: string[];
  adjusted: { name: string; qty: number }[];
};

function cartLineKey(item: CartItem): string {
  return [item.product.id, item.variantSku ?? "", item.size, item.color].join("|");
}

/** Customer-facing print/colour label for a cart line. */
export function cartLineColorLabel(item: CartItem): string {
  const variant = findVariant(item.product, {
    size: item.size,
    color: item.color,
    sku: item.variantSku,
  });
  if (variant?.color && !/^print[-\s]?\d+$/i.test(variant.color.trim())) {
    return variant.color;
  }
  return resolveStorefrontColorLabel(item.product.colors, item.color, item.product.sku);
}

/** Thumbnail for a cart line — uses the selected print/color gallery, not the parent product hero. */
export function cartLineImage(item: CartItem): string {
  const color = cartLineColorLabel(item);
  const { image } = galleryForSelectedColor(item.product, color, item.size, item.variantSku);
  return image || item.product.image;
}

function cartLineColorForLookup(item: CartItem, product: Product): string {
  const label = cartLineColorLabel({ ...item, product });
  if (findVariant(product, { size: item.size, color: label, sku: item.variantSku })) return label;
  if (findVariant(product, { size: item.size, color: item.color, sku: item.variantSku })) return item.color;
  return label;
}

function maxQtyForLine(product: Product, item: CartItem): number {
  const variant = findVariant(product, { size: item.size, color: item.color, sku: item.variantSku });
  if (isVariantsProduct(product) && variant) {
    return Math.min(FIT_LIMITS.qtyMax, Math.max(0, variant.stock));
  }
  if (product.stock != null) {
    return Math.min(FIT_LIMITS.qtyMax, Math.max(0, product.stock));
  }
  return FIT_LIMITS.qtyMax;
}

/** True when a cart line can still be purchased from the live (active) catalog. */
export function isCartLineSellable(item: CartItem, live: Product | undefined): live is Product {
  if (!live) return false;
  const variant = findVariant(live, { size: item.size, color: item.color, sku: item.variantSku });
  if (isVariantsProduct(live)) {
    return isVariantSellable(variant);
  }
  return isSimpleProductSellable(live);
}

/** Drop inactive/unavailable lines and refresh prices + qty caps from the catalog. */
export function sanitizeCartWithCatalog(cart: CartItem[], products: Product[]): CartSyncResult {
  const byId = new Map(products.map((p) => [p.id, p]));
  const removed: string[] = [];
  const adjusted: { name: string; qty: number }[] = [];
  const next: CartItem[] = [];

  for (const item of cart) {
    const live = byId.get(item.product.id);
    if (!isCartLineSellable(item, live)) {
      removed.push(item.product.name);
      continue;
    }

    const lookupColor = cartLineColorForLookup(item, live);
    const variant = findVariant(live, { size: item.size, color: lookupColor, sku: item.variantSku });
    const maxQty = maxQtyForLine(live, item);
    if (maxQty <= 0) {
      removed.push(item.product.name);
      continue;
    }

    const qty = Math.min(item.qty, maxQty);
    if (qty < item.qty) {
      adjusted.push({ name: item.product.name, qty });
    }

    const unitPrice = lineUnitPrice(live, {
      size: item.size,
      color: item.color,
      sku: item.variantSku ?? variant?.sku,
    });

    next.push({
      ...item,
      product: live,
      qty,
      unitPrice,
      variantSku: variant?.sku ?? item.variantSku,
      color: lookupColor,
    });
  }

  return { cart: next, removed, adjusted };
}

export function cartsEqual(a: CartItem[], b: CartItem[]): boolean {
  if (a.length !== b.length) return false;
  const mapB = new Map(b.map((item) => [cartLineKey(item), item]));
  for (const item of a) {
    const other = mapB.get(cartLineKey(item));
    if (!other || other.qty !== item.qty || other.unitPrice !== item.unitPrice) return false;
  }
  return true;
}

export type CartPricingTotals = {
  subtotal: number;
  mrpTotal: number;
  productDiscount: number;
};

/** MRP, store discount, and selling subtotal for cart / checkout summaries. */
export function cartPricingTotals(cart: CartItem[]): CartPricingTotals {
  let subtotal = 0;
  let mrpTotal = 0;
  for (const item of cart) {
    const unitPrice = item.unitPrice;
    const unitMrp = lineUnitMrp(item.product, {
      size: item.size,
      color: item.color,
      sku: item.variantSku,
    });
    subtotal += roundInr(item.qty * unitPrice);
    mrpTotal += roundInr(item.qty * unitMrp);
  }
  subtotal = roundInr(subtotal);
  mrpTotal = roundInr(mrpTotal);
  return {
    subtotal,
    mrpTotal,
    productDiscount: roundInr(Math.max(0, mrpTotal - subtotal)),
  };
}

export function formatCartSyncMessage(result: CartSyncResult): string {
  const parts: string[] = [];
  if (result.removed.length === 1) {
    parts.push(`“${result.removed[0]}” was removed — no longer available`);
  } else if (result.removed.length > 1) {
    parts.push(`${result.removed.length} items were removed — no longer available`);
  }
  if (result.adjusted.length === 1) {
    const { name, qty } = result.adjusted[0];
    parts.push(`quantity for “${name}” updated to ${qty}`);
  } else if (result.adjusted.length > 1) {
    parts.push(`quantities updated for ${result.adjusted.length} items`);
  }
  return parts.join(". ");
}
