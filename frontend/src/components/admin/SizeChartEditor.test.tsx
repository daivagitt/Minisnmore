import { useState } from "react";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { SizeChartEditor } from "./SizeChartEditor";
import type { ProductSizeChart } from "@/lib/product-size-charts";

vi.mock("sonner", () => ({
  toast: {
    error: vi.fn(),
    success: vi.fn(),
    warning: vi.fn(),
  },
}));

function chartWith(zeroToThree: number | string): ProductSizeChart {
  return {
    code: "T",
    title: "Test",
    tolerance: "±1 cm",
    sections: [
      {
        title: "Vest",
        bands: ["0-3M", "3-6M"],
        rows: [
          { label: "Chest width", values: { "0-3M": zeroToThree, "3-6M": 24 } },
          { label: "Body length", values: { "0-3M": 28, "3-6M": 30 } },
        ],
      },
    ],
  };
}

function lastChestValue(onChange: ReturnType<typeof vi.fn>): number | string | undefined {
  const last = onChange.mock.calls.at(-1)?.[0] as ProductSizeChart | undefined;
  return last?.sections[0]?.rows[0]?.values["0-3M"];
}

function renderEditor(initial: ProductSizeChart) {
  const onChange = vi.fn();
  function Harness() {
    const [value, setValue] = useState<ProductSizeChart | null>(initial);
    return (
      <SizeChartEditor
        value={value}
        onChange={(next) => {
          onChange(next);
          setValue(next);
        }}
      />
    );
  }
  render(<Harness />);
  return { onChange };
}

describe("SizeChartEditor 0-3M range", () => {
  it("shows an existing 0-3M from–to range in the start and end fields", () => {
    renderEditor(chartWith("22-23"));

    expect(screen.getAllByLabelText("Range from")[0]).toHaveValue("22");
    expect(screen.getAllByLabelText("Range to")[0]).toHaveValue("23");
  });

  it("keeps the start value in the from field while typing 0-3M", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor(chartWith(""));

    const from = screen.getAllByLabelText("Range from")[0]!;
    const to = screen.getAllByLabelText("Range to")[0]!;

    await user.type(from, "26");

    expect(from).toHaveValue("26");
    expect(to).toHaveValue("");
    expect(onChange).not.toHaveBeenCalled();
  });

  it("saves 0-3M start and end together as a range, not concatenated or swapped", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor(chartWith(""));

    const from = screen.getAllByLabelText("Range from")[0]!;
    const to = screen.getAllByLabelText("Range to")[0]!;

    await user.type(from, "26");
    await user.type(to, "28");
    await user.tab();

    expect(from).toHaveValue("26");
    expect(to).toHaveValue("28");
    expect(lastChestValue(onChange)).toBe("26-28");
  });

  it("edits the start of an existing 0-3M range without changing the end", async () => {
    const user = userEvent.setup();
    const { onChange } = renderEditor(chartWith("22-23"));

    const from = screen.getAllByLabelText("Range from")[0]!;
    await user.clear(from);
    await user.type(from, "21");
    await user.tab();

    expect(lastChestValue(onChange)).toBe("21-23");
    expect(screen.getAllByLabelText("Range from")[0]).toHaveValue("21");
    expect(screen.getAllByLabelText("Range to")[0]).toHaveValue("23");
  });
});

describe("SizeChartEditor preview", () => {
  it("shows entered rows on the main chart and uses them for calculator ranges", () => {
    renderEditor({
      code: "MnM-4",
      title: "Shorts Sets",
      tolerance: "±1 cm",
      sections: [
        {
          title: "Measurements",
          bands: ["0-3M", "3-6M", "6-12M", "12-18M", "18-24M"],
          rows: [
            {
              label: "Waistband width",
              values: { "0-3M": "12-16", "3-6M": 17, "6-12M": 18, "12-18M": 19, "18-24M": 20 },
            },
            {
              label: "Waistband Height",
              values: { "0-3M": "1.1-1.5", "3-6M": 1.5, "6-12M": 1.5, "12-18M": 1.5, "18-24M": 1.5 },
            },
          ],
        },
      ],
    });

    expect(screen.getByText("Waistband Height")).toBeInTheDocument();
    expect(screen.getAllByText("Waistband width").length).toBeGreaterThan(0);
    expect(screen.getByText("Size calculator ranges")).toBeInTheDocument();
    expect(screen.getByText("12–16 cm")).toBeInTheDocument();
    expect(screen.queryByText(/No Chest/)).not.toBeInTheDocument();
    expect(screen.queryByText(/No Body length/)).not.toBeInTheDocument();
  });
});
