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
