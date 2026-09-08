import { describe, expect, it } from "vitest";
import {
  filterSizeChartToProductSizes,
  fitPointsFromSizeChart,
  formatMeasurementCell,
  isUsableSizeChart,
  isZeroToThreeBand,
  parseMeasurementCell,
  parseProductSizeChart,
  serializeProductSizeChart,
  resolveSizeChartSku,
  sizeChartForProduct,
  SIZE_CHARTS_BY_SKU,
} from "./product-size-charts";
import { emptySizeChart } from "./size-chart-excel";

describe("product size charts", () => {
  it.each([-10, true, false, [], {}, "-3-5", "Infinity", "garbage"])("rejects invalid measurement %j even beside a valid cell", (cell) => {
    const chart = { code: "", title: "Test", tolerance: "", sections: [{ title: "Vest", bands: ["0-3M", "3-6M"],
      rows: [{ label: "Chest", values: { "0-3M": cell, "3-6M": 24 } }] }] };
    expect(parseProductSizeChart(chart)).toBeNull();
    expect(() => serializeProductSizeChart(chart as never)).toThrow();
  });

  it("keeps zero, blanks and positive ranges distinct", () => {
    expect(parseMeasurementCell(0)).toEqual({ min: null, max: 0 });
    expect(parseMeasurementCell(-1)).toEqual({ min: null, max: null });
    expect(parseMeasurementCell("-2-5")).toEqual({ min: null, max: null });
    expect(parseMeasurementCell("N/A")).toEqual({ min: null, max: null });
    expect(parseMeasurementCell("23–21 cm")).toEqual({ min: 21, max: 23 });
  });

  it("normalizes both sides of custom chart filtering and rejects duplicate aliases", () => {
    const chart = { code: "", title: "Test", tolerance: "", sections: [{ title: "Vest", bands: ["0-3 Months", "3-6 Months"],
      rows: [{ label: "Chest", values: { "0-3 Months": 22, "3-6 Months": 24 } }] }] };
    expect(filterSizeChartToProductSizes(chart, ["0–3M"]).sections[0]?.bands).toEqual(["0-3 Months"]);
    chart.sections[0]!.bands = ["0-3 Months", "0-3M"];
    expect(parseProductSizeChart(chart)).toBeNull();
  });

  it("all approved charts still pass strict parsing", () => {
    for (const chart of Object.values(SIZE_CHARTS_BY_SKU)) expect(parseProductSizeChart(chart), chart.code).not.toBeNull();
  });

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

  it("prefers product sizeChart over SKU map", () => {
    const custom = {
      code: "CUSTOM-1",
      title: "Custom chart",
      tolerance: "±1 cm",
      sections: [
        {
          title: "Vest",
          bands: ["0-3M", "3-6M"],
          rows: [
            { label: "Chest width", values: { "0-3M": 20, "3-6M": 21 } },
            { label: "Body length", values: { "0-3M": 26, "3-6M": 28 } },
          ],
        },
      ],
    };
    const chart = sizeChartForProduct({
      sku: "FRONT-OPEN-VEST-SLEVELESS",
      sizeChart: custom,
    });
    expect(chart?.code).toBe("CUSTOM-1");
    expect(chart?.sections[0]?.rows[0]?.values["0-3M"]).toBe(20);
  });

  it("falls back to SKU map when product sizeChart is empty", () => {
    expect(
      sizeChartForProduct({
        sku: "BASIC-SHORTS",
        sizeChart: { code: "", title: "", tolerance: "", sections: [] },
      })?.code,
    ).toBe("MnM-8");
  });

  it("treats empty starter charts as unusable and keeps the SKU fallback", () => {
    const starter = emptySizeChart({ title: "Draft", code: "" });
    expect(isUsableSizeChart(starter)).toBe(false);
    expect(
      sizeChartForProduct({
        sku: "BASIC-SHORTS",
        sizeChart: starter,
      })?.code,
    ).toBe("MnM-8");
  });

  it("reads blank chest/length cells as null, not 0", () => {
    const points = fitPointsFromSizeChart({
      code: "BLANK",
      title: "Blank",
      tolerance: "±1 cm",
      sections: [
        {
          title: "Piece",
          bands: ["0-3M", "3-6M"],
          rows: [
            { label: "Chest width", values: { "0-3M": "", "3-6M": "—" } },
            { label: "Body length", values: { "0-3M": "n/a", "3-6M": "" } },
          ],
        },
      ],
    });
    expect(points).toEqual([
      { band: "0-3M", lengthCm: null, chestCm: null },
      { band: "3-6M", lengthCm: null, chestCm: null },
    ]);
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

  it("parses 0-3M min–max cells without concatenating digits", () => {
    expect(parseMeasurementCell("25-27")).toEqual({ min: 25, max: 27 });
    expect(parseMeasurementCell("21–22 cm")).toEqual({ min: 21, max: 22 });
    expect(parseMeasurementCell(28)).toEqual({ min: null, max: 28 });
    expect(parseMeasurementCell("")).toEqual({ min: null, max: null });
    expect(parseMeasurementCell("n/a")).toEqual({ min: null, max: null });
    expect(formatMeasurementCell(25, 27)).toBe("25-27");
    expect(formatMeasurementCell(null, 28)).toBe(28);
    expect(isZeroToThreeBand("0-3 M")).toBe(true);
    expect(isZeroToThreeBand("3-6M")).toBe(false);

    const points = fitPointsFromSizeChart({
      code: "RANGE",
      title: "Range",
      tolerance: "±1 cm",
      sections: [
        {
          title: "Piece",
          bands: ["0-3M", "3-6M"],
          rows: [
            { label: "Chest width", values: { "0-3M": "21-22", "3-6M": 24 } },
            { label: "Body length", values: { "0-3M": "25-27", "3-6M": 30 } },
          ],
        },
      ],
    });
    expect(points[0]).toEqual({
      band: "0-3M",
      lengthCm: 27,
      chestCm: 22,
      lengthMin: 25,
      chestMin: 21,
    });
    expect(points[1]).toEqual({ band: "3-6M", lengthCm: 30, chestCm: 24 });
  });
});
