import { describe, expect, it } from "vitest";
import { stockAvailabilityState } from "@/components/StockAvailability";
import { parseVariantsJson } from "@/lib/product-variants";
import type { Product } from "@/data/products";

const matrixProduct: Product = {
  id: "1",
  slug: "horizon",
  name: "Horizon Tee",
  tagline: "",
  price: 799,
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
    { sku: "HORIZON-S-NAVY", size: "S", color: "Navy", rate: 799, mrp: 999, stock: 5, active: true },
    { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", rate: 799, mrp: 999, stock: 2, active: true },
    { sku: "HORIZON-L-IVORY", size: "L", color: "Ivory", rate: 799, mrp: 999, stock: 0, active: true },
  ]),
};

const simpleProduct: Product = {
  id: "2",
  slug: "cloud-swaddle",
  name: "Cloud Swaddle",
  tagline: "",
  price: 499,
  image: "/y.jpg",
  gallery: ["/y.jpg"],
  ages: ["0-3m"],
  fabric: "Muslin",
  colors: [{ name: "Cream", hex: "#f7e3d5" }],
  sizes: ["One Size"],
  rating: 5,
  reviewCount: 0,
  category: "Newborn",
  description: "",
  itemStructure: "SIMPLE",
};

describe("stockAvailabilityState — simple (non-variant) products", () => {
  it("treats untracked stock (null/undefined) as always in stock", () => {
    const state = stockAvailabilityState(simpleProduct, undefined, {});
    expect(state.kind).toBe("in_stock");
  });

  it("flags zero stock as out of stock", () => {
    const state = stockAvailabilityState({ ...simpleProduct, stock: 0 }, undefined, {});
    expect(state.kind).toBe("out_of_stock");
    expect(state.label).toBe("Out of stock");
  });

  it("flags low stock for small tracked quantities", () => {
    const state = stockAvailabilityState({ ...simpleProduct, stock: 2 }, undefined, {});
    expect(state.kind).toBe("low_stock");
    expect(state.count).toBe(2);
  });

  it("shows in stock for healthy tracked quantities", () => {
    const state = stockAvailabilityState({ ...simpleProduct, stock: 25 }, undefined, {});
    expect(state.kind).toBe("in_stock");
  });
});

describe("stockAvailabilityState", () => {
  it("prompts before size and colour are chosen", () => {
    const state = stockAvailabilityState(matrixProduct, undefined, { size: null, color: null });
    expect(state.kind).toBe("prompt");
    expect(state.label).toMatch(/select size/i);
  });

  it("shows in stock without exposing raw counts", () => {
    const variant = matrixProduct.variants?.[0];
    const state = stockAvailabilityState(matrixProduct, variant, { size: "S", color: "Navy" });
    expect(state.kind).toBe("in_stock");
    expect(state.label).toBe("In stock");
  });

  it("shows low stock urgency for small quantities", () => {
    const variant = matrixProduct.variants?.[1];
    const state = stockAvailabilityState(matrixProduct, variant, { size: "M", color: "Navy" });
    expect(state.kind).toBe("low_stock");
    expect(state.label).toBe("Only 2 left");
    expect(state.count).toBe(2);
  });

  it("shows out of stock for zero inventory", () => {
    const variant = matrixProduct.variants?.[2];
    const state = stockAvailabilityState(matrixProduct, variant, { size: "L", color: "Ivory" });
    expect(state.kind).toBe("out_of_stock");
    expect(state.label).toBe("Out of stock");
  });
});
