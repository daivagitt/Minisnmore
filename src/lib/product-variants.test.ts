import { describe, expect, it } from "vitest";
import {
  colorsForSize,
  findVariant,
  isVariantSellable,
  isVariantsProduct,
  lineUnitPrice,
  parseVariantsJson,
  sizeHasSellableVariants,
  variantStockMessage,
} from "./product-variants";
import type { Product } from "@/data/products";

const matrixProduct: Product = {
  id: "1",
  slug: "horizon",
  name: "Horizon Tee",
  tagline: "",
  price: 799.2,
  compareAt: 999,
  image: "/x.jpg",
  gallery: ["/x.jpg"],
  ages: ["6-12m"],
  fabric: "Organic Cotton",
  colors: [
    { name: "Navy", hex: "#2c3e58" },
    { name: "Ivory", hex: "#f2eee8" },
  ],
  sizes: ["S", "M", "L"],
  rating: 5,
  reviewCount: 0,
  category: "Infant",
  description: "",
  itemStructure: "VARIANTS",
  variants: parseVariantsJson([
    { sku: "HORIZON-S-NAVY", size: "S", color: "Navy", rate: 999, stock: 3, active: true },
    { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", rate: 1199, stock: 4, active: true },
    { sku: "HORIZON-L-IVORY", size: "L", color: "Ivory", rate: 1299, stock: 0, active: true },
  ]),
};

describe("product-variants", () => {
  it("parses variants and filters colors by size", () => {
    expect(isVariantsProduct(matrixProduct)).toBe(true);
    expect(colorsForSize(matrixProduct, "S")).toEqual(["Navy"]);
    expect(colorsForSize(matrixProduct, "L")).toEqual(["Ivory"]);
    expect(findVariant(matrixProduct, { size: "S", color: "Ivory" })).toBeUndefined();
  });

  it("treats VARIANTS with empty matrix as variants but not sellable", () => {
    const broken = { ...matrixProduct, variants: [] };
    expect(isVariantsProduct(broken)).toBe(true);
    expect(isVariantSellable(findVariant(broken, { size: "S", color: "Navy" }))).toBe(false);
  });

  it("computes line price and sellable from selected variant", () => {
    expect(lineUnitPrice(matrixProduct, { size: "M", color: "Navy" })).toBe(959.2);
    expect(isVariantSellable(findVariant(matrixProduct, { size: "M", color: "Navy" }))).toBe(true);
    expect(isVariantSellable(findVariant(matrixProduct, { size: "L", color: "Ivory" }))).toBe(false);
  });

  it("flags sold-out sizes and surfaces stock copy for shoppers", () => {
    expect(sizeHasSellableVariants(matrixProduct, "M")).toBe(true);
    expect(sizeHasSellableVariants(matrixProduct, "L")).toBe(false);
    expect(
      variantStockMessage(matrixProduct, findVariant(matrixProduct, { size: "L", color: "Ivory" }), {
        size: "L",
        color: "Ivory",
      }),
    ).toMatch(/sold out/i);
    expect(variantStockMessage(matrixProduct, undefined, { size: "S", color: "Ivory" })).toMatch(/not available/i);
  });
});
