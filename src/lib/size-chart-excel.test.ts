import { describe, expect, it } from "vitest";
import {
  emptySizeChart,
  sizeChartFromExcelSheets,
  validateSizeChartStructure,
} from "./size-chart-excel";
import { parseProductSizeChart } from "./product-size-charts";

describe("size chart excel", () => {
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

  it("warns when chest/length rows are missing", () => {
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
});
