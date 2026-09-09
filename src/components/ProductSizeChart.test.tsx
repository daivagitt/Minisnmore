import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProductSizeChartTables } from "./ProductSizeChart";
import { exampleSizeChartForImport } from "@/lib/size-chart-excel";

describe("full garment chart", () => {
  it("retains the compact guide and exposes every section and measurement", () => {
    render(
      <ProductSizeChartTables
        rows={[{ size: "0-3M", length: "26-28", chest: "22-23", lengthCm: 28, chestCm: 23 }]}
        chart={exampleSizeChartForImport()}
      />,
    );
    expect(screen.getByText("All garment measurements")).toBeInTheDocument();
    expect(screen.getByText("Vest")).toBeInTheDocument();
    expect(screen.getByText("Shorts")).toBeInTheDocument();
    expect(screen.getByText("Inseam")).toBeInTheDocument();
    expect(screen.getByText("Waistband width")).toBeInTheDocument();
  });
});
