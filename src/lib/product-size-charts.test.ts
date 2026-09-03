import { describe, expect, it } from "vitest";
import {
  filterSizeChartToProductSizes,
  fitPointsFromSizeChart,
  resolveSizeChartSku,
  sizeChartForProduct,
  SIZE_CHARTS_BY_SKU,
} from "./product-size-charts";

describe("product size charts", () => {
  it("resolves every catalog parent SKU to an approved chart", () => {
    const skus = [
      "LOOP-BACK-FLEECE-KNIT-FRONT-OPEN-VEST-FULL-SLEEVE",
      "FRONT-OPEN-VEST-SLEVELESS",
      "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET",
      "BOOK-STYLE-TYING-JABLA",
      "BOOK-STYLE-TYING-JABLA-SHORT-SET",
      "FRONT-OPEN-VEST-HALF-SLEEVE",
      "FRONT-OPEN-VEST-HALF-SLEEVE-SHORT-SET",
      "BASIC-SHORTS",
      "SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE",
      "SIDE-BUTTON-T-SHIRT-HALF-SLEEVE",
      "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE",
      "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE-BLOOMERS",
      "BODY-SUIT-HALF-SLEEVE",
      "ROUND-CAP",
      "CAP-SOCKS-MITTEN-SET",
      "PACK-OF-3-MITTENS",
      "SOCKS",
      "TYING-CAP",
      "FACE-NAPKIN-WASH-CLOTH-10-10",
      "BIBS-SPECIAL",
      "BABY-SWADDLE",
      "BABY-TERRY-TOWEL-FLUFFY",
    ];
    for (const sku of skus) {
      expect(SIZE_CHARTS_BY_SKU[sku], sku).toBeTruthy();
      expect(sizeChartForProduct({ sku })?.code).toMatch(/^MnM-\d+$/);
    }
  });

  it("prefers SKU over name and maps common name aliases", () => {
    expect(resolveSizeChartSku({ sku: "BABY-SWADDLE", name: "Socks" })).toBe("BABY-SWADDLE");
    expect(resolveSizeChartSku({ name: "Book Style Tying Jabla short set" })).toBe(
      "BOOK-STYLE-TYING-JABLA-SHORT-SET",
    );
    expect(resolveSizeChartSku({ slug: "baby-front-open-frock" })).toBe(
      "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE",
    );
    expect(resolveSizeChartSku({ name: "Side button T Shirt & Shorts Set (Half sleeve)" })).toBe(
      "SIDE-BUTTON-T-SHIRT-SHORTS-SET-HALF-SLEEVE",
    );
    expect(resolveSizeChartSku({ name: "Unknown piece" })).toBeNull();
  });

  it("includes garment + bottoms sections for set products", () => {
    const chart = sizeChartForProduct({ sku: "FRONT-OPEN-VEST-SLEEVELESS-SHORT-SET" });
    expect(chart?.sections.map((s) => s.title)).toEqual(["Vest", "Shorts"]);
    expect(chart?.sections[0]?.rows.find((r) => r.label === "Chest width")?.values["0-3M"]).toBe(23);
    expect(chart?.sections[1]?.rows.find((r) => r.label === "Inseam")?.values["18-24M"]).toBe(9);
  });

  it("keeps MnM-13 body-suit half-chest values from the approved sheet", () => {
    const chart = sizeChartForProduct({ sku: "BODY-SUIT-HALF-SLEEVE" });
    expect(chart?.code).toBe("MnM-13");
    expect(chart?.sections[0]?.rows.find((r) => r.label === "Chest (½)")?.values).toEqual({
      "0-3M": 21,
      "3-6M": 22,
      "6-12M": 24,
      "12-18M": 25,
      "18-24M": 26,
    });
  });

  it("filters age columns to sizes the product sells", () => {
    const full = sizeChartForProduct({ sku: "BASIC-SHORTS" })!;
    const filtered = filterSizeChartToProductSizes(full, ["0-3 M", "3-6 M", "6-12 M"]);
    expect(filtered.sections[0]?.bands).toEqual(["0-3M", "3-6M", "6-12M"]);
    expect(filtered.sections[0]?.rows[0]?.values["12-18M"]).toBeUndefined();
    expect(filtered.sections[0]?.rows[0]?.values["0-3M"]).toBe(16);
  });

  it("leaves common-size charts unchanged when filtering", () => {
    const chart = sizeChartForProduct({ sku: "BABY-SWADDLE" })!;
    const filtered = filterSizeChartToProductSizes(chart, ["One Size"]);
    expect(filtered.sections[0]?.bands).toEqual(["Single size"]);
    expect(filtered.sections[0]?.rows[0]?.values["Single size"]).toBe(100);
  });

  it("extracts length and chest fit points from MnM garment rows", () => {
    const vest = sizeChartForProduct({ sku: "FRONT-OPEN-VEST-SLEVELESS" })!;
    expect(fitPointsFromSizeChart(vest)).toEqual([
      { band: "0-3M", lengthCm: 28, chestCm: 23 },
      { band: "3-6M", lengthCm: 30, chestCm: 24 },
      { band: "6-12M", lengthCm: 32, chestCm: 25 },
      { band: "12-18M", lengthCm: 34, chestCm: 26 },
      { band: "18-24M", lengthCm: 36, chestCm: 27 },
    ]);

    const frock = sizeChartForProduct({ sku: "BABY-FRONT-OPEN-FROCK-HALF-SLEEVE" })!;
    expect(fitPointsFromSizeChart(frock)[0]).toEqual({
      band: "0-3M",
      lengthCm: 30,
      chestCm: 24,
    });

    const shorts = sizeChartForProduct({ sku: "BASIC-SHORTS" })!;
    expect(fitPointsFromSizeChart(shorts)[0]).toEqual({
      band: "0-3M",
      lengthCm: 17,
      chestCm: null,
    });
  });
});
