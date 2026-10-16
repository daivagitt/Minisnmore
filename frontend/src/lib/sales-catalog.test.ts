import { describe, expect, it } from "vitest";
import { salesCatalogTotals, salesProductInventory, salesVariantLabel } from "./sales-catalog";

describe("salesVariantLabel", () => {
  it("joins size and colour", () => {
    expect(salesVariantLabel({ size: "0-3m", color: "Cream" })).toBe("0-3m · Cream");
  });

  it("falls back when size and colour are missing", () => {
    expect(salesVariantLabel({})).toBe("One size");
  });
});

describe("salesProductInventory", () => {
  it("lists each variant with its stock count", () => {
    const inventory = salesProductInventory({
      sku: "JB",
      stock: 0,
      variants_json: JSON.stringify([
        { sku: "JB-12-SG", size: "12-18m", color: "Sage", stock: 2, active: true },
        { sku: "JB-03-CR", size: "0-3m", color: "Cream", stock: 8, active: true },
        { sku: "JB-03-SG", size: "0-3m", color: "Sage", stock: 0, active: false },
      ]),
    });

    expect(inventory.variantCount).toBe(3);
    expect(inventory.totalStock).toBe(10);
    expect(inventory.lines.map((line) => line.sku)).toEqual(["JB-03-CR", "JB-03-SG", "JB-12-SG"]);
    expect(inventory.lines[0]).toMatchObject({
      label: "0-3m · Cream",
      size: "0-3m",
      color: "Cream",
      stock: 8,
      active: true,
    });
    expect(inventory.lines[1]).toMatchObject({
      label: "0-3m · Sage",
      size: "0-3m",
      color: "Sage",
      stock: 0,
      active: false,
    });
  });

  it("sorts by size and colour fields, not the display label", () => {
    const inventory = salesProductInventory({
      sku: "JB",
      variants_json: JSON.stringify([
        { sku: "JB-B", size: "3-6m", color: "Sage · Print", stock: 1, active: true },
        { sku: "JB-A", size: "0-3m", color: "Cream", stock: 1, active: true },
      ]),
    });
    expect(inventory.lines.map((line) => line.sku)).toEqual(["JB-A", "JB-B"]);
    expect(inventory.lines[1]).toMatchObject({ size: "3-6m", color: "Sage · Print" });
  });

  it("uses parent stock when a product has no variants", () => {
    expect(salesProductInventory({ sku: "WRAP", stock: 7, active: true, variants_json: null })).toEqual({
      variantCount: 0,
      totalStock: 7,
      lines: [{ sku: "WRAP", label: "No variants", size: "", color: "", stock: 7, active: true }],
    });
  });
});

describe("salesCatalogTotals", () => {
  it("sums products, variants, and units", () => {
    expect(
      salesCatalogTotals([
        {
          sku: "JB",
          variants_json: JSON.stringify([
            { sku: "JB-A", size: "0-3m", color: "Cream", stock: 4 },
            { sku: "JB-B", size: "3-6m", color: "Cream", stock: 1 },
          ]),
        },
        { sku: "WRAP", stock: 5, variants_json: null },
      ]),
    ).toEqual({ productCount: 2, variantCount: 2, unitCount: 10 });
  });
});
