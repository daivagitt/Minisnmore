import { describe, expect, it } from "vitest";
import {
  applyExpectedPrintNamesToColors,
  applyExpectedPrintNamesToVariantsJson,
  resolveStorefrontColorLabel,
} from "./expected-prints";

describe("expected-prints", () => {
  it("maps Baby Swaddle Print-N slots to catalog display names", () => {
    const colors = applyExpectedPrintNamesToColors(
      [
        { name: "Print-1", imageUrls: ["https://cdn.example.com/brown.jpg"] },
        { name: "Print-2", imageUrls: ["https://cdn.example.com/green.jpg"] },
        { name: "Print-3", imageUrls: ["https://cdn.example.com/white.jpg"] },
      ],
      "BABY-SWADDLE",
    );
    expect(colors.map((c) => c.name)).toEqual(["BROWN SWADDLE", "GREEN SWADDLE", "WHITE SWADDLE"]);
  });

  it("resolves labels from parent SKU when colours are still generic in the API", () => {
    expect(
      resolveStorefrontColorLabel(
        [{ name: "Print-1" }, { name: "Print-2" }],
        "Print-1",
        "BABY-SWADDLE",
      ),
    ).toBe("BROWN SWADDLE");
  });

  it("renames variant matrix colours in variants_json", () => {
    const raw = JSON.stringify([
      { sku: "BABY-SWADDLE-P1", size: "COMMON SIZE", color: "Print-1", price: 299, stock: 5, active: true },
      { sku: "BABY-SWADDLE-P2", size: "COMMON SIZE", color: "Print-2", price: 299, stock: 5, active: true },
    ]);
    const next = applyExpectedPrintNamesToVariantsJson(raw, "BABY-SWADDLE");
    const rows = JSON.parse(next!);
    expect(rows.map((r: { color: string }) => r.color)).toEqual(["BROWN SWADDLE", "GREEN SWADDLE"]);
  });

  it("keeps direct Zoho print names unchanged", () => {
    const colors = applyExpectedPrintNamesToColors(
      [{ name: "BABY BLUE" }, { name: "SNAIL & BLUE" }, { name: "HAPPY BEE" }],
      "BASIC-SHORTS",
    );
    expect(colors.map((c) => c.name)).toEqual(["BABY BLUE", "SNAIL & BLUE", "HAPPY BEE"]);

    const raw = JSON.stringify([
      { sku: "BASIC-SHORTS-BABY-BLUE", size: "0-3 M", color: "BABY BLUE", stock: 5, active: true },
      { sku: "BASIC-SHORTS-SNAIL-BLUE", size: "0-3 M", color: "SNAIL & BLUE", stock: 5, active: true },
    ]);
    const next = applyExpectedPrintNamesToVariantsJson(raw, "BASIC-SHORTS");
    expect(JSON.parse(next!).map((r: { color: string }) => r.color)).toEqual(["BABY BLUE", "SNAIL & BLUE"]);
  });
});
