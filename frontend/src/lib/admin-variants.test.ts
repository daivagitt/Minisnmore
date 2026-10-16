import { describe, expect, it } from "vitest";
import {
  aggregateFromVariantRows,
  buildVariantMatrixFromSizesColors,
  normalizeAdminProductPricing,
  regenerateVariantSkus,
  suggestParentSku,
  suggestVariantSku,
  summarizeZohoVariantLinkage,
  zohoItemGroupId,
  variantAmountBlur,
  variantAmountInput,
  rowsFromVariantsJson,
  serializeVariantRows,
} from "./admin-variants";

describe("admin-variants SKU helpers", () => {
  it("suggests parent SKU from product name", () => {
    expect(suggestParentSku("Baby Swaddle")).toBe("BABY-SWADDLE");
    expect(suggestParentSku("  Horizon Tee  ")).toBe("HORIZON-TEE");
  });

  it("suggests variant SKU as PARENT-SIZE-COLOR", () => {
    expect(suggestVariantSku("HORIZON", "M", "Navy")).toBe("HORIZON-M-NAVY");
  });

  it("builds matrix from sizes and colours preserving existing rows", () => {
    const existing = [
      {
        sku: "HORIZON-S-NAVY",
        size: "S",
        color: "Navy",
        rate: 800,
        mrp: 1000,
        cost: "",
        stock: 3,
        active: true,
      },
    ];
    const rows = buildVariantMatrixFromSizesColors(["S", "M"], ["Navy", "Ivory"], "HORIZON", existing);
    expect(rows).toHaveLength(4);
    const kept = rows.find((r) => r.size === "S" && r.color === "Navy");
    expect(kept?.stock).toBe(3);
    expect(kept?.rate).toBe(800);
    expect(rows.find((r) => r.size === "M" && r.color === "Ivory")?.sku).toBe("HORIZON-M-IVORY");
  });

  it("regenerates variant SKUs when parent SKU changes", () => {
    const rows = regenerateVariantSkus(
      [{ sku: "OLD-S-NAVY", size: "S", color: "Navy", rate: 800, mrp: 1000, cost: "", stock: 1, active: true }],
      "NEW",
    );
    expect(rows[0].sku).toBe("NEW-S-NAVY");
    expect(rows[0].stock).toBe(1);
  });

  it("keeps partial decimals while typing prices", () => {
    expect(variantAmountInput("799")).toBe(799);
    expect(variantAmountInput("799.")).toBe("799.");
    expect(variantAmountBlur("799.")).toBe(799);
    expect(variantAmountInput("0.0")).toBe("0.0");
    expect(variantAmountInput("2.67")).toBe("2.67");
    expect(variantAmountBlur("2.67")).toBe(2.67);
    expect(variantAmountBlur("999")).toBe(999);
  });

  it("aggregateFromVariantRows keeps catalog sizes without variant rows", () => {
    const agg = aggregateFromVariantRows(
      [
        {
          sku: "VEST-03-BLUE",
          size: "0-3 M",
          color: "Blue",
          rate: 400,
          mrp: 500,
          cost: "",
          stock: 2,
          active: true,
        },
      ],
      ["0-3 M", "3-6 M", "6-12 M", "12-18 M"],
    );
    expect(agg.sizes).toEqual(["0-3 M", "3-6 M", "6-12 M", "12-18 M"]);
  });

  it("summarizeZohoVariantLinkage counts linked variant rows", () => {
    const json = JSON.stringify([
      { sku: "A-S", size: "S", color: "Red", rate: 100, stock: 1, active: true, zohoItemId: "z1" },
      { sku: "A-M", size: "M", color: "Red", rate: 100, stock: 1, active: true },
    ]);
    expect(summarizeZohoVariantLinkage(json).label).toBe("1/2 variant item(s) in Zoho");
  });

  it("derives variant selling from MRP when loading admin rows", () => {
    const json = JSON.stringify([
      { sku: "VEST-S", size: "S", color: "Blue", rate: 999, mrp: 1000, stock: 1, active: true },
    ]);
    const rows = rowsFromVariantsJson(json, 20);
    expect(rows[0]?.mrp).toBe(1000);
    expect(rows[0]?.rate).toBe(800);
  });

  it("normalizes simple admin product selling from MRP", () => {
    const normalized = normalizeAdminProductPricing(
      { item_structure: "SIMPLE", price: 1000, compare_at_price: 1000 },
      20,
    );
    expect(normalized.compare_at_price).toBe(1000);
    expect(normalized.price).toBe(800);
  });

  it("keeps cleared MRP at zero and does not promote rate back to MRP", () => {
    const cleared = serializeVariantRows([
      { sku: "VEST-S", size: "S", color: "Blue", rate: 2, mrp: 0, cost: "", stock: 1, active: true },
    ]);
    expect(JSON.parse(cleared)[0].mrp).toBe(0);
    expect(JSON.parse(cleared)[0].rate).toBe(2);

    const rows = rowsFromVariantsJson(cleared, 25);
    expect(rows[0]?.mrp).toBe(0);
    expect(rows[0]?.rate).toBe(2);

    const agg = aggregateFromVariantRows(rows, [], 25);
    expect(agg.compare_at_price).toBeNull();
    expect(agg.price).toBe(2);
  });

  it("keeps empty MRP cleared when serialized as null", () => {
    const cleared = serializeVariantRows([
      { sku: "VEST-S", size: "S", color: "Blue", rate: 2, mrp: "", cost: "", stock: 1, active: true },
    ]);
    expect(JSON.parse(cleared)[0].mrp).toBeNull();
    const rows = rowsFromVariantsJson(cleared, 25);
    expect(rows[0]?.mrp).toBe("");
    expect(rows[0]?.rate).toBe(2);
  });

  it("still treats legacy rate-only rows as MRP", () => {
    const rows = rowsFromVariantsJson(
      JSON.stringify([{ sku: "LEGACY-S", size: "S", color: "Blue", rate: 1000, stock: 1, active: true }]),
      20,
    );
    expect(rows[0]?.mrp).toBe(1000);
    expect(rows[0]?.rate).toBe(800);
  });

  it("round-trips per-size imageUrl on same-colour rows", () => {
    const json = JSON.stringify([
      {
        sku: "KEDAR-03-PINK",
        size: "0-3M",
        color: "Pink",
        rate: 100,
        stock: 1,
        active: true,
        imageUrl: "https://cdn.example.com/pink-03.jpg",
      },
      {
        sku: "KEDAR-36-PINK",
        size: "3-6M",
        color: "Pink",
        rate: 100,
        stock: 1,
        active: true,
        imageUrl: "https://cdn.example.com/pink-36.jpg",
      },
    ]);
    const rows = rowsFromVariantsJson(json);
    expect(rows[0]?.imageUrl).toBe("https://cdn.example.com/pink-03.jpg");
    expect(rows[1]?.imageUrl).toBe("https://cdn.example.com/pink-36.jpg");
    const out = serializeVariantRows(rows);
    expect(out).toContain("pink-03.jpg");
    expect(out).toContain("pink-36.jpg");
  });
});

describe("zohoItemGroupId", () => {
  it("returns digits-only parent id when it is not a variant item id", () => {
    expect(
      zohoItemGroupId({
        item_structure: "VARIANTS",
        zoho_item_id: "3624052000000394593",
        variants_json: JSON.stringify([
          { sku: "JB-03", size: "0-3", color: "Navy", zohoItemId: "3624052000000394600", active: true },
        ]),
      }),
    ).toBe("3624052000000394593");
  });

  it("returns null when parent id is itself a variant item id", () => {
    expect(
      zohoItemGroupId({
        item_structure: "VARIANTS",
        zoho_item_id: "3624052000000394600",
        variants_json: JSON.stringify([
          { sku: "JB-03", size: "0-3", color: "Navy", zohoItemId: "3624052000000394600", active: true },
        ]),
      }),
    ).toBeNull();
  });

  it("returns null when parent id is not digits", () => {
    expect(
      zohoItemGroupId({
        item_structure: "VARIANTS",
        zoho_item_id: "grp-horizon",
        variants_json: "[]",
      }),
    ).toBeNull();
  });
});
