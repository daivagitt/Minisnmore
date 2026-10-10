/** @vitest-environment jsdom */
import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { OrderProductThumb } from "./OrderProductThumb";

vi.mock("@/components/LazyImage", () => ({
  LazyImage: ({ src, alt }: { src: string; alt?: string }) => <img src={src} alt={alt ?? ""} />,
}));

describe("OrderProductThumb", () => {
  it("renders the product image", () => {
    render(<OrderProductThumb src="https://cdn.example/dress.jpg" alt="Floral Cotton Dress" />);
    expect(screen.getByRole("img", { name: "Floral Cotton Dress" })).toHaveAttribute(
      "src",
      "https://cdn.example/dress.jpg.thumb.jpg",
    );
  });

  it("renders a placeholder when the image is missing", () => {
    const { container } = render(<OrderProductThumb src="" alt="Missing" />);
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(container.querySelector("[aria-hidden]")).toBeTruthy();
  });
});
