import type { ProductVariant, VariantProductLike } from "@/lib/product-variants";
import { isVariantsProduct } from "@/lib/product-variants";

export type StockAvailabilityKind =
  | "prompt"
  | "unavailable"
  | "inactive"
  | "out_of_stock"
  | "low_stock"
  | "in_stock";

export type StockAvailabilityState = {
  kind: StockAvailabilityKind;
  label: string;
  count?: number;
};

const LOW_STOCK_THRESHOLD = 3;

/** Structured availability for PDP / quick-view (no raw SKU, no amateur counts). */
export function stockAvailabilityState(
  product: VariantProductLike,
  variant: ProductVariant | undefined,
  opts: { size?: string | null; color?: string | null },
): StockAvailabilityState {
  if (!isVariantsProduct(product)) {
    const stock = product.stock;
    if (stock != null && stock <= 0) {
      return { kind: "out_of_stock", label: "Out of stock" };
    }
    if (stock != null && stock <= LOW_STOCK_THRESHOLD) {
      return { kind: "low_stock", label: `Only ${stock} left`, count: stock };
    }
    return { kind: "in_stock", label: "In stock" };
  }
  if (!opts.size || !opts.color) {
    return { kind: "prompt", label: "Select size & colour to check availability" };
  }
  if (!variant) {
    return { kind: "unavailable", label: "Not available in this combination" };
  }
  if (!variant.active) {
    return { kind: "inactive", label: "No longer available" };
  }
  if (variant.stock <= 0) {
    return { kind: "out_of_stock", label: "Out of stock" };
  }
  if (variant.stock <= LOW_STOCK_THRESHOLD) {
    return {
      kind: "low_stock",
      label: `Only ${variant.stock} left`,
      count: variant.stock,
    };
  }
  return { kind: "in_stock", label: "In stock" };
}

const KIND_STYLES: Record<
  StockAvailabilityKind,
  { dot: string; text: string; wrap?: string }
> = {
  prompt: {
    dot: "bg-muted-foreground/40",
    text: "text-muted-foreground",
  },
  unavailable: {
    dot: "bg-muted-foreground/50",
    text: "text-muted-foreground",
  },
  inactive: {
    dot: "bg-muted-foreground/50",
    text: "text-muted-foreground",
  },
  out_of_stock: {
    dot: "bg-destructive",
    text: "text-destructive font-medium",
    wrap: "bg-destructive/8 rounded-lg px-3 py-1.5",
  },
  low_stock: {
    dot: "bg-amber-500",
    text: "text-amber-800 dark:text-amber-200 font-medium",
    wrap: "bg-amber-500/10 rounded-lg px-3 py-1.5",
  },
  in_stock: {
    dot: "bg-emerald-500",
    text: "text-emerald-700 dark:text-emerald-300 font-medium",
  },
};

export function StockAvailability({
  state,
  className = "",
}: {
  state: StockAvailabilityState;
  className?: string;
}) {
  const styles = KIND_STYLES[state.kind];
  return (
    <div
      className={`flex items-center gap-2 text-sm leading-snug ${styles.wrap ?? ""} ${className}`}
      role="status"
      aria-live="polite"
    >
      <span
        className={`h-2 w-2 shrink-0 rounded-full ${styles.dot} ${state.kind === "in_stock" ? "shadow-[0_0_0_3px_rgba(16,185,129,0.25)]" : ""}`}
        aria-hidden
      />
      <span className={styles.text}>{state.label}</span>
      {state.kind === "low_stock" && (
        <span className="text-[11px] text-amber-700/80 dark:text-amber-200/80">— order soon</span>
      )}
    </div>
  );
}
