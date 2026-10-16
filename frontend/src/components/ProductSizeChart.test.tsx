import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProductSizeChartTables } from "./ProductSizeChart";
import { exampleSizeChartForImport } from "@/lib/size-chart-excel";

describe("full garment chart", () => {
  it("shows every entered section and measurement as the main chart", () => {
    render(
      <ProductSizeChartTables
        rows={[
          {
            size: "0-3M",
            length: "26-28",
            chest: "22-23",
            lengthCm: 28,
            chestCm: 23,
            lengthLabel: "Body length",
            chestLabel: "Chest width",
          },
        ]}
        chart={exampleSizeChartForImport()}
      />,
    );
    expect(screen.queryByText("All garment measurements")).not.toBeInTheDocument();
    expect(screen.getByText("Vest")).toBeInTheDocument();
    expect(screen.getByText("Shorts")).toBeInTheDocument();
    expect(screen.getByText("Inseam")).toBeInTheDocument();
    expect(screen.getByText("Waistband width")).toBeInTheDocument();
    expect(screen.getByText("Size calculator ranges")).toBeInTheDocument();
    expect(screen.getAllByText("Body length").length).toBeGreaterThan(0);
    expect(screen.getAllByText("Chest width").length).toBeGreaterThan(0);
  });

  it("uses entered measurement names when chest/length rows are absent", () => {
    render(
      <ProductSizeChartTables
        rows={[
          {
            size: "0-3M",
            length: "—",
            chest: "12–16 cm",
            lengthCm: null,
            chestCm: 16,
            lengthLabel: null,
            chestLabel: "Waistband width",
          },
        ]}
        chart={{
          code: "MnM-4",
          title: "Shorts Sets",
          tolerance: "±1 cm",
          sections: [
            {
              title: "Measurements",
              bands: ["0-3M", "3-6M"],
              rows: [
                { label: "Waistband width", values: { "0-3M": "12-16", "3-6M": 17 } },
                { label: "Waistband Height", values: { "0-3M": "1.1-1.5", "3-6M": 1.5 } },
              ],
            },
          ],
        }}
      />,
    );
    expect(screen.getByText("Waistband Height")).toBeInTheDocument();
    expect(screen.getAllByText("Waistband width").length).toBeGreaterThan(0);
    expect(screen.queryByText("Length")).not.toBeInTheDocument();
    expect(screen.getByText("12–16 cm")).toBeInTheDocument();
  });
});
