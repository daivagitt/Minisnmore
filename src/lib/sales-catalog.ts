import type { ProductRow } from "@/lib/backend-data";
import { sizeBandOrderIndex } from "@/lib/product-ages";
import { parseVariantsJson, type ProductVariant } from "@/lib/product-variants";

export type SalesInventoryLine = {
  sku: string;
  label: string;
  size: string;
  color: string;
  stock: number;
  active: boolean;
};

export type SalesProductInventory = {
  variantCount: number;
  totalStock: number;
  lines: SalesInventoryLine[];
};

type SalesProductSource = Pick<ProductRow, "sku" | "variants_json"> &
  Partial<Pick<ProductRow, "stock" | "active">>;

export function salesVariantLabel(variant: Pick<ProductVariant, "size" | "color">): string {
  const parts = [variant.size, variant.color].map((value) => value?.trim()).filter(Boolean);
  return parts.length ? parts.join(" · ") : "One size";
}

function compareLines(a: SalesInventoryLine, b: SalesInventoryLine): number {
  const sizeDiff = sizeBandOrderIndex(a.size) - sizeBandOrderIndex(b.size);
  if (sizeDiff !== 0) return sizeDiff;
  const colorDiff = a.color.localeCompare(b.color, "en", { sensitivity: "base" });
  if (colorDiff !== 0) return colorDiff;
  return a.sku.localeCompare(b.sku);
}

export function salesProductInventory(product: SalesProductSource): SalesProductInventory {
  const variants = parseVariantsJson(product.variants_json);
  if (!variants.length) {
    const stock = Math.max(0, Math.floor(Number(product.stock) || 0));
    return {
      variantCount: 0,
      totalStock: stock,
      lines: [
        {
          sku: product.sku?.trim() || "—",
          label: "No variants",
          size: "",
          color: "",
          stock,
          active: product.active !== false,
        },
      ],
    };
  }

  const lines = variants
    .map((variant) => ({
      sku: variant.sku,
      label: salesVariantLabel(variant),
      size: variant.size?.trim() ?? "",
      color: variant.color?.trim() ?? "",
      stock: variant.stock,
      active: variant.active,
    }))
    .sort(compareLines);

  return {
    variantCount: lines.length,
    totalStock: lines.reduce((sum, line) => sum + line.stock, 0),
    lines,
  };
}

export function salesCatalogTotals(products: SalesProductSource[]): {
  productCount: number;
  variantCount: number;
  unitCount: number;
} {
  return products.reduce(
    (totals, product) => {
      const inventory = salesProductInventory(product);
      return {
        productCount: totals.productCount + 1,
        variantCount: totals.variantCount + inventory.variantCount,
        unitCount: totals.unitCount + inventory.totalStock,
      };
    },
    { productCount: 0, variantCount: 0, unitCount: 0 },
  );
}
