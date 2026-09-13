import { render } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { ProductGallery } from "./ProductGallery";

describe("ProductGallery hero", () => {
  it("wraps the photo in a cream frame and does not force a square box", () => {
    const { container } = render(
      <ProductGallery name="Muslin wrap" image="https://cdn.example.com/wrap.jpg" />,
    );

    const img = container.querySelector("img");
    expect(img).toHaveClass("object-contain");
    expect(img).not.toHaveClass("object-cover");
    expect(container.innerHTML).not.toContain("aspect-ratio: 1 / 1");
    expect(container.innerHTML).toContain("bg-cream");
    expect(container.innerHTML).toContain("p-1.5 sm:p-2");
    expect(container.innerHTML).not.toContain("p-2.5 sm:p-3");
  });
});

describe("ProductGallery thumbnails", () => {
  it("gives each thumb an explicit square so they cannot collapse and stack", () => {
    const { getAllByRole } = render(
      <ProductGallery
        name="Fleece vest"
        image="https://cdn.example.com/hero.jpg"
        gallery={[
          "https://cdn.example.com/front.jpg",
          "https://cdn.example.com/back.jpg",
          "https://cdn.example.com/lifestyle.jpg",
        ]}
      />,
    );

    const thumbs = getAllByRole("button").filter((el) =>
      (el.getAttribute("aria-label") ?? "").startsWith("View image"),
    );
    expect(thumbs).toHaveLength(4);
    const strip = thumbs[0]?.parentElement;
    expect(strip?.getAttribute("aria-label")).toBe("Product image thumbnails");
    expect(strip?.style.maxHeight).toContain("70vh");
    for (const thumb of thumbs) {
      expect(thumb.className).toContain("size-[3.5rem]");
      expect(thumb.className).toContain("overflow-hidden");
      expect(thumb.className).toContain("isolate");
      expect(thumb.className).not.toContain("aspect-square");
      const img = thumb.querySelector("img");
      expect(img?.className).toContain("object-contain");
      expect(img?.className).not.toContain("object-cover");
      expect(img?.className).not.toContain("absolute");
    }
  });
});
