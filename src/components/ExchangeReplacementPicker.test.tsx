/** @vitest-environment jsdom */
import { fireEvent, render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { ExchangeReplacementPicker } from "./ExchangeReplacementPicker";
import type { Product } from "@/data/products";

vi.mock("@/components/LazyImage", () => ({
  LazyImage: ({ src, alt }: { src: string; alt?: string }) => <img src={src} alt={alt ?? ""} />,
}));

const product: Product = {
  id: "1",
  slug: "horizon",
  name: "Horizon Tee",
  tagline: "",
  price: 499,
  image: "/x.jpg",
  gallery: ["/x.jpg"],
  ages: ["6-12m"],
  fabric: "Organic Cotton",
  colors: [
    { name: "Navy", hex: "#2c3e58" },
    { name: "Ivory", hex: "#f2eee8" },
  ],
  sizes: ["S", "M"],
  rating: 5,
  reviewCount: 0,
  category: "Infant",
  description: "",
  itemStructure: "VARIANTS",
  variants: [
    { sku: "HORIZON-S-NAVY", size: "S", color: "Navy", price: 499, stock: 3, active: true },
    { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", price: 599, stock: 4, active: true },
    { sku: "HORIZON-M-IVORY", size: "M", color: "Ivory", price: 699, stock: 2, active: true },
  ],
};

describe("ExchangeReplacementPicker", () => {
  it("updates the price difference as a more expensive size is selected", () => {
    const onSizeChange = vi.fn();
    const onColorChange = vi.fn();
    const { rerender } = render(
      <ExchangeReplacementPicker
        product={product}
        originalSize="S"
        originalColor="Navy"
        originalUnitPrice={499}
        qty={2}
        size="S"
        color="Navy"
        onSizeChange={onSizeChange}
        onColorChange={onColorChange}
      />,
    );

    expect(screen.getByText("Same price as your original item")).toBeTruthy();
    expect(screen.getByRole("button", { name: /M/ }).textContent).toContain("+₹200");

    fireEvent.click(screen.getByRole("button", { name: /M/ }));
    expect(onSizeChange).toHaveBeenCalledWith("M");

    rerender(
      <ExchangeReplacementPicker
        product={product}
        originalSize="S"
        originalColor="Navy"
        originalUnitPrice={499}
        qty={2}
        size="M"
        color="Navy"
        onSizeChange={onSizeChange}
        onColorChange={onColorChange}
      />,
    );

    expect(screen.getByText("You'll pay ₹200 extra")).toBeTruthy();
    expect(screen.getByText("S · Navy")).toBeTruthy();
    expect(screen.getByText("M · Navy · qty 2")).toBeTruthy();
  });

  it("shows a refund when a cheaper colour is selected", () => {
    render(
      <ExchangeReplacementPicker
        product={product}
        originalSize="M"
        originalColor="Ivory"
        originalUnitPrice={699}
        qty={1}
        size="M"
        color="Navy"
        onSizeChange={vi.fn()}
        onColorChange={vi.fn()}
      />,
    );

    expect(screen.getByText("You'll get ₹100 back")).toBeTruthy();
    expect(screen.getByRole("button", { name: /Navy, −₹100/i })).toBeTruthy();
  });
});
