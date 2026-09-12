import { fireEvent, render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { ProductSizeCalculator } from "./ProductSizeCalculator";

const limits = {
  lengthMin: 26,
  lengthMax: 36,
  chestMin: 22,
  chestMax: 27,
  hasLength: true,
  hasChest: true,
};

describe("ProductSizeCalculator", () => {
  it("freezes length and chest until a size chart is configured", () => {
    const onLengthChange = vi.fn();
    const onChestChange = vi.fn();

    render(
      <ProductSizeCalculator
        configured={false}
        limits={limits}
        lengthCm="32"
        chestCm="25"
        onLengthChange={onLengthChange}
        onChestChange={onChestChange}
        sizeFit={null}
      />,
    );

    const length = screen.getByLabelText("Garment length in centimetres");
    const chest = screen.getByLabelText("Chest width in centimetres");
    expect(length).toBeDisabled();
    expect(chest).toBeDisabled();
    expect(length).toHaveValue(null);
    expect(chest).toHaveValue(null);
    expect(screen.getByText(/locked until a size chart is added/i)).toBeInTheDocument();

    fireEvent.change(length, { target: { value: "30" } });
    fireEvent.change(chest, { target: { value: "24" } });
    expect(onLengthChange).not.toHaveBeenCalled();
    expect(onChestChange).not.toHaveBeenCalled();
  });

  it("accepts measurements once a size chart is configured", async () => {
    const onLengthChange = vi.fn();

    render(
      <ProductSizeCalculator
        configured
        limits={limits}
        lengthCm=""
        chestCm=""
        onLengthChange={onLengthChange}
        onChestChange={vi.fn()}
        sizeFit={null}
      />,
    );

    const length = screen.getByLabelText("Garment length in centimetres");
    expect(length).toBeEnabled();
    await userEvent.type(length, "3");
    expect(onLengthChange).toHaveBeenCalled();
  });
});
