import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { VariantColorButtons } from "./VariantPickers";

const product = {
  price: 999,
  sizes: ["0-3m"],
  colors: [
    { name: "Bird", hex: "#c8a", swatchUrl: "https://cdn.example.com/bird-swatch.jpg" },
    { name: "Bee", hex: "#fc0", swatchUrl: "https://cdn.example.com/bee-swatch.jpg" },
  ],
};

describe("VariantColorButtons", () => {
  it("loads print swatches as 240px thumbs and prefetches on hover", () => {
    const onColorChange = vi.fn();
    const onColorIntent = vi.fn();
    const { getByRole } = render(
      <VariantColorButtons
        product={product}
        size="0-3m"
        color="Bird"
        onColorChange={onColorChange}
        onColorIntent={onColorIntent}
        colors={product.colors}
      />,
    );

    const bird = getByRole("img", { name: "Bird" });
    expect(bird).toHaveAttribute("src", "https://cdn.example.com/bird-swatch.jpg.thumb.jpg");
    expect(bird).toHaveAttribute("loading", "eager");
    expect(bird).toHaveAttribute("fetchpriority", "low");

    fireEvent.pointerEnter(getByRole("button", { name: "Bee" }));
    expect(onColorIntent).toHaveBeenCalledWith("Bee");
    fireEvent.click(getByRole("button", { name: "Bee" }));
    expect(onColorChange).toHaveBeenCalledWith("Bee");
  });
});
