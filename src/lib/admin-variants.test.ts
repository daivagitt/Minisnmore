import { describe, expect, it } from "vitest";
import {
  buildVariantMatrixFromSizesColors,
  regenerateVariantSkus,
  suggestParentSku,
  suggestVariantSku,
  variantAmountBlur,
  variantAmountInput,
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
    expect(variantAmountInput("0.0")).toBe(0);
    expect(variantAmountBlur("999")).toBe(999);
  });
});
