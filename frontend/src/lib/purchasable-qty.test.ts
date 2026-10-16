import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import { FIT_LIMITS } from "@/lib/product-fit";
import { maxPurchasableQty } from "@/lib/purchasable-qty";

const base: Product = {
  id: "p1",
  slug: "romper",
  name: "Romper",
  tagline: "Soft",
  price: 500,
  image: "/romper.jpg",
  gallery: ["/romper.jpg"],
  ages: ["0-3m"],
  fabric: "Organic Cotton",
  colors: [{ name: "Cream", hex: "#fff" }],
  sizes: ["0-3m"],
  rating: 5,
  reviewCount: 1,
  category: "Newborn",
  categorySlug: "",
  categoryName: "",
  sku: "ROMPER",
  description: "Soft romper",
  stock: 5,
};

describe("maxPurchasableQty", () => {
  it("caps purchasable qty to variant or simple stock", () => {
    const variantProduct: Product = {
      ...base,
      id: "p-var",
      itemStructure: "VARIANTS",
      sizes: ["S", "M"],
      variants: [
        { sku: "SET-S-CREAM", size: "S", color: "Cream", price: 799, stock: 2, active: true },
        { sku: "SET-M-CREAM", size: "M", color: "Cream", price: 799, stock: 8, active: true },
      ],
    };
    expect(maxPurchasableQty(variantProduct, { size: "S", color: "Cream" })).toBe(2);
    expect(maxPurchasableQty(variantProduct, { size: "M", color: "Cream" })).toBe(8);
    expect(maxPurchasableQty({ ...base, stock: 2 })).toBe(2);
    expect(maxPurchasableQty({ ...base, stock: null })).toBe(FIT_LIMITS.qtyMax);
    expect(maxPurchasableQty(variantProduct, { size: "S", color: "Missing" })).toBe(0);
  });
});
