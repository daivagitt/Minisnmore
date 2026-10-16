import { describe, expect, it } from "vitest";
import {
  catalogSizesForProduct,
  availableSizesForFit,
  colorsForSize,
  colorSelectionForSize,
  defaultSellableSelection,
  findVariant,
  isSimpleProductSellable,
  isVariantSellable,
  isVariantsProduct,
  lineUnitMrp,
  lineUnitPrice,
  parseVariantsJson,
  sellableColorsForSize,
  sizeHasActiveVariants,
  sizeHasSellableVariants,
  sizesForDisplay,
  storefrontListPricing,
  variantStockMessage,
} from "./product-variants";
import type { Product } from "@/data/products";

describe("parseVariantsJson", () => {
  it("keeps per-SKU imageUrl", () => {
    expect(
      parseVariantsJson([
        {
          sku: "KEDAR-03-PINK",
          size: "0-3M",
          color: "Pink",
          rate: 100,
          stock: 1,
          active: true,
          imageUrl: "https://cdn.example.com/pink-03.jpg",
        },
      ])[0].imageUrl,
    ).toBe("https://cdn.example.com/pink-03.jpg");
  });
});

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
    expect(lineUnitMrp(matrixProduct, { size: "M", color: "Navy" })).toBe(1199);
    expect(isVariantSellable(findVariant(matrixProduct, { size: "M", color: "Navy" }))).toBe(true);
    expect(isVariantSellable(findVariant(matrixProduct, { size: "L", color: "Ivory" }))).toBe(false);
  });

  it("treats simple (non-variant) products as sellable only when stock isn't exhausted", () => {
    const simple = { ...matrixProduct, itemStructure: "SIMPLE", variants: undefined };
    expect(isSimpleProductSellable({ ...simple, stock: undefined })).toBe(true);
    expect(isSimpleProductSellable({ ...simple, stock: null })).toBe(true);
    expect(isSimpleProductSellable({ ...simple, stock: 5 })).toBe(true);
    expect(isSimpleProductSellable({ ...simple, stock: 0 })).toBe(false);
    expect(isSimpleProductSellable(null)).toBe(false);
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

  it("defaults to first sellable size in ascending order", () => {
    const ageProduct: Product = {
      ...matrixProduct,
      sizes: ["L", "S", "M"],
      variants: parseVariantsJson([
        { sku: "HORIZON-S-NAVY", size: "S", color: "Navy", rate: 999, stock: 3, active: true },
        { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", rate: 1199, stock: 4, active: true },
        { sku: "HORIZON-L-IVORY", size: "L", color: "Ivory", rate: 1299, stock: 0, active: true },
      ]),
    };
    expect(defaultSellableSelection(ageProduct)).toEqual({
      size: "S",
      color: "Navy",
      variant: findVariant(ageProduct, { size: "S", color: "Navy" }),
    });
    expect(sellableColorsForSize(ageProduct, "S")).toEqual(["Navy"]);
    expect(sellableColorsForSize(ageProduct, "L")).toEqual([]);
  });

  it("shows all four baby age bands even when catalog sizes are incomplete", () => {
    const partial: Product = {
      ...matrixProduct,
      sizes: ["0-3 M", "6-12 M"],
      itemStructure: "VARIANTS",
      variants: parseVariantsJson([
        { sku: "VEST-03-BLUE", size: "0-3 M", color: "Blue", rate: 400, stock: 2, active: true },
        { sku: "VEST-612-BLUE", size: "6-12 M", color: "Blue", rate: 400, stock: 0, active: true },
      ]),
    };
    expect(sizesForDisplay(partial)).toEqual(["0-3 M", "3-6 M", "6-12 M", "12-18 M"]);
    expect(sizeHasSellableVariants(partial, "3-6 M")).toBe(false);
    expect(sizeHasSellableVariants(partial, "0-3 M")).toBe(true);
    expect(sizeHasSellableVariants(partial, "6-12 M")).toBe(false);
    expect(sizeHasActiveVariants(partial, "6-12 M")).toBe(true);
    expect(availableSizesForFit(partial)).toEqual(["0-3 M", "6-12 M"]);
  });

  it("includes zero-stock catalog sizes in the size calculator but not inactive sizes", () => {
    const mixed: Product = {
      ...matrixProduct,
      sizes: ["S", "M", "L"],
      variants: parseVariantsJson([
        { sku: "HORIZON-S-NAVY", size: "S", color: "Navy", rate: 999, stock: 0, active: true },
        { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", rate: 1199, stock: 4, active: true },
        { sku: "HORIZON-L-IVORY", size: "L", color: "Ivory", rate: 1299, stock: 0, active: false },
      ]),
    };
    expect(sizeHasActiveVariants(mixed, "S")).toBe(true);
    expect(sizeHasSellableVariants(mixed, "S")).toBe(false);
    expect(sizeHasActiveVariants(mixed, "L")).toBe(false);
    expect(availableSizesForFit(mixed)).toEqual(["S", "M"]);
  });

  it("lists only catalog/variant sizes without standard-band padding", () => {
    const vest: Product = {
      ...matrixProduct,
      sizes: ["0-3 M", "3-6 M", "6-12 M"],
      itemStructure: "VARIANTS",
      variants: parseVariantsJson([
        { sku: "VEST-03-P1", size: "0-3 M", color: "Print 1", rate: 400, stock: 2, active: true },
        { sku: "VEST-36-P1", size: "3-6 M", color: "Print 1", rate: 400, stock: 2, active: true },
        { sku: "VEST-612-P1", size: "6-12 M", color: "Print 1", rate: 400, stock: 2, active: true },
      ]),
    };
    expect(catalogSizesForProduct(vest)).toEqual(["0-3 M", "3-6 M", "6-12 M"]);
    expect(sizesForDisplay(vest)).toEqual(["0-3 M", "3-6 M", "6-12 M", "12-18 M"]);
    expect(availableSizesForFit(vest)).toEqual(["0-3 M", "3-6 M", "6-12 M"]);
  });

  it("infers VARIANTS from variant matrix when itemStructure missing", () => {
    const inferred: Product = {
      ...matrixProduct,
      itemStructure: undefined,
      variants: parseVariantsJson([
        { sku: "VEST-03-BLUE", size: "0-3 M", color: "Blue", rate: 400, stock: 4, active: true },
      ]),
    };
    expect(isVariantsProduct(inferred)).toBe(true);
  });

  it("keeps SIMPLE products simple even when stray variant rows exist", () => {
    const simpleWithRows: Product = {
      ...matrixProduct,
      itemStructure: "SIMPLE",
      variants: parseVariantsJson([
        { sku: "VEST-03-BLUE", size: "0-3 M", color: "Blue", rate: 400, stock: 4, active: true },
      ]),
    };
    expect(isVariantsProduct(simpleWithRows)).toBe(false);
    expect(sizeHasSellableVariants(simpleWithRows, "S")).toBe(true);
  });

  it("treats simple products with zero stock as unsellable for every size", () => {
    const simple = { ...matrixProduct, itemStructure: "SIMPLE", variants: undefined, stock: 0 };
    expect(sizeHasSellableVariants(simple, "S")).toBe(false);
    expect(sizeHasSellableVariants(simple, "M")).toBe(false);
  });

  it("matches variant rows when size labels differ only by spacing", () => {
    const spaced: Product = {
      ...matrixProduct,
      sizes: ["0-3m"],
      itemStructure: "VARIANTS",
      variants: parseVariantsJson([
        { sku: "VEST-03-BLUE", size: "0-3 M", color: "Blue", rate: 400, stock: 4, active: true },
      ]),
    };
    expect(sizeHasSellableVariants(spaced, "0-3m")).toBe(true);
    expect(findVariant(spaced, { size: "0-3m", color: "Blue" })?.sku).toBe("VEST-03-BLUE");
  });

  it("uses default sellable variant for list/card price instead of parent min", () => {
    const skewed: Product = {
      ...matrixProduct,
      // Parent row is min-of-matrix (can be a bad/outlier variant rate).
      price: 1.64,
      compareAt: 2,
      sizes: ["0-3m", "3-6m"],
      variants: parseVariantsJson([
        { sku: "SHORTS-03-BLUE", size: "0-3m", color: "Navy", rate: 320, mrp: 400, stock: 5, active: true },
        { sku: "SHORTS-36-ODD", size: "3-6m", color: "Navy", rate: 1.64, mrp: 2, stock: 35, active: true },
      ]),
    };
    expect(storefrontListPricing(skewed)).toEqual({ price: 320, compareAt: 400 });
  });
});

describe("colorSelectionForSize", () => {
  const product = {
    sizes: ["S"], colors: [], price: 100, itemStructure: "VARIANTS",
    variants: [
      { sku: "blue", size: "S", color: "Blue", price: 100, stock: 0, active: true },
      { sku: "red", size: "S", color: "Red", price: 100, stock: 3, active: true },
    ],
  };

  it("preserves the selected sold-out colour", () => {
    expect(colorSelectionForSize(product, "S", "Blue")).toBe("Blue");
  });

  it("prefers an in-stock colour for a new selection", () => {
    expect(colorSelectionForSize(product, "S")).toBe("Red");
  });

  it("falls back to an active colour when all stock is zero", () => {
    const soldOut = { ...product, variants: product.variants.map((v) => ({ ...v, stock: 0 })) };
    expect(colorSelectionForSize(soldOut, "S")).toBe("Blue");
    expect(isVariantSellable(findVariant(soldOut, { size: "S", color: "Blue" }))).toBe(false);
  });
});
