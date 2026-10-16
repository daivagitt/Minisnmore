import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import {
  describeExchangePriceDifference,
  exchangeLineAmounts,
  formatSignedInrDifference,
  representativeReplacementUnitPrice,
  selectedReplacementUnitPrice,
} from "./exchange-price-difference";
const product: Product = {
  id: "1",
  slug: "horizon",
  name: "Horizon Tee",
  tagline: "",
  price: 499,
  image: "/x.jpg",
  gallery: ["/x.jpg"],
  ages: ["6-12m"],
  fabric: "Organic Cotton",
  colors: [
    { name: "Navy", hex: "#2c3e58" },
    { name: "Ivory", hex: "#f2eee8" },
  ],
  sizes: ["S", "M"],
  rating: 5,
  reviewCount: 0,
  category: "Infant",
  description: "",
  itemStructure: "VARIANTS",
  variants: [
    { sku: "HORIZON-S-NAVY", size: "S", color: "Navy", price: 499, stock: 3, active: true },
    { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", price: 599, stock: 4, active: true },
    { sku: "HORIZON-M-IVORY", size: "M", color: "Ivory", price: 699, stock: 2, active: true },
  ],
};

describe("exchange price difference", () => {
  it("collects extra when the replacement line costs more", () => {
    const amounts = exchangeLineAmounts({ originalUnitPrice: 499, replacementUnitPrice: 599, qty: 2 });
    expect(amounts).toEqual({ originalAmount: 998, replacementAmount: 1198, difference: 200, qty: 2 });
    expect(describeExchangePriceDifference(amounts.difference)).toMatchObject({
      kind: "collect",
      label: "You'll pay ₹200 extra",
    });
    expect(formatSignedInrDifference(amounts.difference)).toBe("+₹200");
  });

  it("refunds when the replacement line costs less", () => {
    const amounts = exchangeLineAmounts({ originalUnitPrice: 599, replacementUnitPrice: 499, qty: 1 });
    expect(amounts.difference).toBe(-100);
    expect(describeExchangePriceDifference(amounts.difference)).toMatchObject({
      kind: "refund",
      label: "You'll get ₹100 back",
    });
    expect(formatSignedInrDifference(amounts.difference)).toBe("−₹100");
  });

  it("treats matching prices as no difference", () => {
    const amounts = exchangeLineAmounts({ originalUnitPrice: 499, replacementUnitPrice: 499, qty: 1 });
    expect(amounts.difference).toBe(0);
    expect(describeExchangePriceDifference(0).kind).toBe("same");
    expect(formatSignedInrDifference(0)).toBeNull();
  });

  it("uses the selected colour price, otherwise the first sellable colour for that size", () => {
    expect(selectedReplacementUnitPrice(product, "M", "Ivory")).toBe(699);
    expect(representativeReplacementUnitPrice(product, "M", "Ivory")).toBe(699);
    expect(representativeReplacementUnitPrice(product, "S", "Ivory")).toBe(499);
  });
});
