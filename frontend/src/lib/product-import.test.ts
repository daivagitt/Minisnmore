import { describe, expect, it } from "vitest";
import { parseProductImportRow } from "./product-import";

const categories = [{ id: "3", slug: "newborn" }];
const input = {
  slug: "baby-romper", name: "Baby Romper", sku: "ROMPER", category_slug: "newborn",
  price: 100, stock: 5, age_range: "0, 12", fabric_type: "Cotton", active: "TRUE",
};

describe("product spreadsheet import", () => {
  it("normalizes template values and image aliases before saving", () => {
    expect(parseProductImportRow({ ...input, colors: "Cream, Navy", sizes: "0-3m, 3-6m", image_url: "/romper.jpg" }, categories))
      .toMatchObject({ category_id: "3", price: 100, stock: 5, age_min_months: 0, age_max_months: 12,
        colors: [{ name: "Cream" }, { name: "Navy" }], images: ["/romper.jpg"], sizes: ["0-3m", "3-6m"] });
  });

  it("rejects missing categories and invalid required fields before persistence", () => {
    expect(() => parseProductImportRow({ ...input, category_slug: "missing" }, categories)).toThrow("Unknown category slug");
    expect(() => parseProductImportRow({ ...input, price: "", stock: -1 }, categories)).toThrow("Price is required");
    expect(() => parseProductImportRow({ ...input, age_range: "12, 0" }, categories)).toThrow("Age max");
  });

  it("preserves configured galleries and variant data when updating a SKU", () => {
    const existing = {
      ...parseProductImportRow(input, categories), id: "7", images: ["/existing.jpg"], description: "Existing story",
      size_notes: { "0-3m": "Grippers" }, item_structure: "VARIANTS",
      variants_json: JSON.stringify([{ sku: "ROMPER-S", size: "S", color: "Navy", rate: 125, stock: 3, active: true }]),
    };
    const result = parseProductImportRow({ ...input, price: 110 }, categories, existing);
    expect(result).toMatchObject({ id: "7", price: 110, images: ["/existing.jpg"], description: "Existing story", size_notes: existing.size_notes });
    expect(result.item_structure).toBe("VARIANTS");
    expect(result.variants_json).toBe(existing.variants_json);
  });
});
