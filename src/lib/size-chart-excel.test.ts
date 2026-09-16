import { describe, expect, it } from "vitest";
import {
  emptySizeChart,
  exampleSizeChartForImport,
  parseSizeChartExcelFile,
  sizeChartDataAoA,
  sizeChartFromExcelSheets,
  sizeChartGuideAoA,
  sizeChartMetaAoA,
  sizeChartToXlsxBuffer,
  validateSizeChartStructure,
} from "./size-chart-excel";
import { parseProductSizeChart } from "./product-size-charts";

describe("size chart excel", () => {
  it.each([true, false, -1, [], {}, "-2-4"])("rejects invalid Excel cell %j without coercion", (cell) => {
    expect(() => sizeChartFromExcelSheets({ chartRows: [{ Section: "Vest", Measurement: "Chest", "0-3M": cell, "3-6M": 24 }] })).toThrow();
  });

  it("parses Meta + Chart rows into ProductSizeChart", () => {
    const chart = sizeChartFromExcelSheets({
      metaRows: [
        { Field: "title", Value: "Test vest" },
        { Field: "code", Value: "T-1" },
        { Field: "tolerance", Value: "±1 cm" },
      ],
      chartRows: [
        {
          Section: "Vest",
          Measurement: "Chest width",
          "0-3M": 23,
          "3-6M": 24,
          "6-12M": 25,
        },
        {
          Section: "Vest",
          Measurement: "Body length",
          "0-3M": 28,
          "3-6M": 30,
          "6-12M": 32,
        },
        {
          Section: "Shorts",
          Measurement: "Total length",
          "0-3M": 17,
          "3-6M": 18,
          "6-12M": 20,
        },
      ],
    });

    expect(chart.title).toBe("Test vest");
    expect(chart.code).toBe("T-1");
    expect(chart.sections).toHaveLength(2);
    expect(chart.sections[0]?.title).toBe("Vest");
    expect(chart.sections[0]?.bands).toEqual(["0-3M", "3-6M", "6-12M"]);
    expect(chart.sections[0]?.rows.find((r) => r.label === "Chest width")?.values["0-3M"]).toBe(23);
    expect(chart.sections[1]?.title).toBe("Shorts");
    expect(validateSizeChartStructure(chart).ok).toBe(true);
    expect(validateSizeChartStructure(chart).warnings).toEqual([]);
  });

  it("round-trips through JSON parse helper", () => {
    const original = emptySizeChart({
      code: "X",
      title: "Round trip",
      sections: [
        {
          title: "Piece",
          bands: ["0-3M", "3-6M"],
          rows: [
            { label: "Chest width", values: { "0-3M": 20, "3-6M": 21 } },
            { label: "Body length", values: { "0-3M": 27, "3-6M": 29 } },
          ],
        },
      ],
    });
    const parsed = parseProductSizeChart(JSON.stringify(original));
    expect(parsed).toEqual(original);
  });

  it("warns when no size-varying calculator rows exist", () => {
    const chart = sizeChartFromExcelSheets({
      chartRows: [
        {
          Section: "Cap",
          Measurement: "Head circumference",
          "Common size": 42,
        },
      ],
    });
    const result = validateSizeChartStructure(chart);
    expect(result.ok).toBe(true);
    expect(result.warnings.length).toBeGreaterThan(0);
  });

  it("does not warn when custom size-varying rows can drive the calculator", () => {
    const chart = sizeChartFromExcelSheets({
      chartRows: [
        {
          Section: "Measurements",
          Measurement: "Waistband width",
          "0-3M": "12-16",
          "3-6M": 17,
          "6-12M": 18,
        },
        {
          Section: "Measurements",
          Measurement: "Waistband Height",
          "0-3M": "1.1-1.5",
          "3-6M": 1.5,
          "6-12M": 1.5,
        },
      ],
    });
    expect(validateSizeChartStructure(chart)).toEqual({ ok: true, errors: [], warnings: [] });
  });

  it("keeps 0-3M min–max text in Excel cells", () => {
    const chart = sizeChartFromExcelSheets({
      chartRows: [
        {
          Section: "Vest",
          Measurement: "Chest width",
          "0-3M": "21-22",
          "3-6M": 24,
        },
        {
          Section: "Vest",
          Measurement: "Body length",
          "0-3M": "25-27",
          "3-6M": 30,
        },
      ],
    });
    expect(chart.sections[0]?.rows.find((r) => r.label === "Body length")?.values["0-3M"]).toBe(
      "25-27",
    );
    expect(validateSizeChartStructure(chart).ok).toBe(true);
  });

  it("ships a complete vest + shorts Excel example that imports cleanly", async () => {
    const sample = exampleSizeChartForImport();
    expect(validateSizeChartStructure(sample)).toEqual({ ok: true, errors: [], warnings: [] });
    expect(sample.sections.map((s) => s.title)).toEqual(["Vest", "Shorts"]);
    expect(sample.sections[0]?.rows.find((r) => r.label === "Chest width")?.values["0-3M"]).toBe(
      "22-23",
    );
    expect(sample.sections[0]?.rows.find((r) => r.label === "Body length")?.values["0-3M"]).toBe(
      "26-28",
    );

    const chartAoA = sizeChartDataAoA(sample);
    expect(chartAoA[0]).toEqual(["Section", "Measurement", "0-3M", "3-6M", "6-12M", "12-18M", "18-24M"]);
    expect(sizeChartMetaAoA(sample)[1]).toEqual(["title", sample.title]);
    expect(sizeChartGuideAoA().some((row) => String(row[0]) === "0-3M")).toBe(true);

    const buf = await sizeChartToXlsxBuffer(sample, true);
    const parsed = await parseSizeChartExcelFile(new File([buf], "product-size-chart-example.xlsx"));

    expect(parsed.title).toBe(sample.title);
    expect(parsed.code).toBe("MnM-EXAMPLE");
    expect(parsed.sections).toHaveLength(2);
    expect(parsed.sections[0]?.rows.find((r) => r.label === "Body length")?.values["0-3M"]).toBe(
      "26-28",
    );
    expect(parsed.sections[0]?.rows.find((r) => r.label === "Chest width")?.values["3-6M"]).toBe(24);
    expect(parsed.sections[1]?.title).toBe("Shorts");
    expect(validateSizeChartStructure(parsed).warnings).toEqual([]);
  });
});
