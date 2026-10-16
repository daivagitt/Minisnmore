import { fireEvent, render } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import { ProductGallery } from "./ProductGallery";
import { resetStorefrontImagePrefetch } from "@/lib/storefront-image";

function spyHtmlImage() {
  const created: string[] = [];
  const OriginalImage = globalThis.Image;
  class SpyImage {
    decoding = "";
    set src(value: string) {
      created.push(value);
    }
    get src() {
      return created.at(-1) ?? "";
    }
  }
  globalThis.Image = SpyImage as unknown as typeof Image;
  return {
    created,
    restore() {
      globalThis.Image = OriginalImage;
    },
  };
}

describe("ProductGallery hero", () => {
  afterEach(() => {
    resetStorefrontImagePrefetch();
  });
  it("wraps the photo in a cream frame and does not force a square box", () => {
    const { container } = render(
      <ProductGallery name="Muslin wrap" image="https://cdn.example.com/wrap.jpg" />,
    );

    const img = container.querySelector('img[alt="Muslin wrap"]');
    expect(img).toHaveClass("object-contain");
    expect(img).not.toHaveClass("object-cover");
    expect(img).toHaveAttribute("loading", "eager");
    expect(img).toHaveAttribute("fetchpriority", "high");
    expect(img).toHaveAttribute("src", "https://cdn.example.com/wrap.jpg.display.jpg");
    expect(img).toHaveAttribute(
      "srcset",
      "https://cdn.example.com/wrap.jpg.display.jpg 800w, https://cdn.example.com/wrap.jpg 1250w",
    );
    expect(container.innerHTML).not.toContain("aspect-ratio: 1 / 1");
    expect(container.innerHTML).toContain("bg-cream");
    expect(container.innerHTML).toContain("p-1.5 sm:p-2");
    expect(container.innerHTML).not.toContain("p-2.5 sm:p-3");
  });

  it("overlays a size callout on the photo when the selected size has one", () => {
    const { getByText, queryByText, rerender } = render(
      <ProductGallery
        name="Socks"
        image="https://cdn.example.com/socks.jpg"
        sizeNote="This size has grippers"
      />,
    );
    expect(getByText("This size has grippers")).toBeInTheDocument();
    rerender(<ProductGallery name="Socks" image="https://cdn.example.com/socks.jpg" sizeNote={null} />);
    expect(queryByText("This size has grippers")).toBeNull();
  });
});

describe("ProductGallery thumbnails", () => {
  afterEach(() => {
    resetStorefrontImagePrefetch();
  });
  it("shows previous and next arrows on mobile when there are multiple images", () => {
    const { getByRole } = render(
      <ProductGallery
        name="Fleece vest"
        image="https://cdn.example.com/hero.jpg"
        gallery={["https://cdn.example.com/front.jpg"]}
      />,
    );

    const prev = getByRole("button", { name: "Previous image" });
    const next = getByRole("button", { name: "Next image" });
    expect(prev.className).toContain("opacity-100");
    expect(prev.className).toContain("pointer-events-auto");
    expect(prev.className).toContain("lg:opacity-0");
    expect(next.className).toContain("opacity-100");
    expect(next.className).toContain("lg:group-hover:opacity-100");
  });

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
      expect(img).toHaveAttribute("loading", "eager");
      expect(img).toHaveAttribute("fetchpriority", "low");
      expect(img?.getAttribute("src")).toMatch(/\.thumb\.jpg$/);
    }
  });

  it("falls back to the original URL when a thumb is missing", () => {
    const { getAllByRole } = render(
      <ProductGallery
        name="Fleece vest"
        image="https://cdn.example.com/hero.jpg"
        gallery={["https://cdn.example.com/front.jpg"]}
      />,
    );

    const thumbs = getAllByRole("button").filter((el) =>
      (el.getAttribute("aria-label") ?? "").startsWith("View image"),
    );
    const img = thumbs[0]?.querySelector("img");
    expect(img).toHaveAttribute("src", "https://cdn.example.com/hero.jpg.thumb.jpg");
    fireEvent.error(img!);
    expect(img).toHaveAttribute("src", "https://cdn.example.com/hero.jpg");
  });

  it("keeps the hero eager so thumbs do not steal the first network slot", () => {
    const { container } = render(
      <ProductGallery
        name="Fleece vest"
        image="https://cdn.example.com/hero.jpg"
        gallery={["https://cdn.example.com/front.jpg", "https://cdn.example.com/back.jpg"]}
      />,
    );

    const hero = container.querySelector('img[alt="Fleece vest"]');
    expect(hero).toHaveAttribute("loading", "eager");
    expect(hero).toHaveAttribute("fetchpriority", "high");
    expect(hero).toHaveAttribute("src", "https://cdn.example.com/hero.jpg.display.jpg");
    const thumbs = [...container.querySelectorAll("button")]
      .filter((el) => (el.getAttribute("aria-label") ?? "").startsWith("View image"));
    expect(hero!.compareDocumentPosition(thumbs[0]!) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
    const lqip = container.querySelector('img[aria-hidden="true"]');
    expect(lqip).toHaveAttribute("loading", "eager");
    expect(lqip).toHaveAttribute("fetchpriority", "low");
    expect(lqip).toHaveAttribute("src", "https://cdn.example.com/hero.jpg.thumb.jpg");
  });

  it("falls back to the original hero when display size is missing", () => {
    const { container } = render(
      <ProductGallery name="Fleece vest" image="https://cdn.example.com/hero.jpg" />,
    );
    const hero = container.querySelector('img[alt="Fleece vest"]');
    expect(hero).toHaveAttribute("src", "https://cdn.example.com/hero.jpg.display.jpg");
    fireEvent.error(hero!);
    expect(hero).toHaveAttribute("src", "https://cdn.example.com/hero.jpg");
    expect(hero).not.toHaveAttribute("srcset");
  });

  it("does not construct Image for every gallery URL on mount", () => {
    const spy = spyHtmlImage();
    try {
      render(
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
      expect(spy.created.filter(Boolean)).toEqual([]);
    } finally {
      spy.restore();
    }
  });

  it("resets to the first photo and keeps one hero when the print gallery changes", () => {
    const { container, rerender, getAllByRole } = render(
      <ProductGallery
        name="Face Napkin"
        image="https://cdn.example.com/print-a.jpg"
        gallery={["https://cdn.example.com/print-a-2.jpg", "https://cdn.example.com/print-a-3.jpg"]}
      />,
    );

    const thumbs = getAllByRole("button").filter((el) =>
      (el.getAttribute("aria-label") ?? "").startsWith("View image"),
    );
    fireEvent.click(thumbs[2]!);

    rerender(
      <ProductGallery
        name="Face Napkin"
        image="https://cdn.example.com/print-b.jpg"
        gallery={["https://cdn.example.com/print-b-2.jpg", "https://cdn.example.com/print-b-3.jpg"]}
      />,
    );

    const heroes = container.querySelectorAll('img[alt="Face Napkin"]');
    expect(heroes).toHaveLength(1);
    expect(heroes[0]).toHaveAttribute("src", "https://cdn.example.com/print-b.jpg.display.jpg");
  });

  it("keeps the hero frame while the next print image is still loading", () => {
    const { container, rerender } = render(
      <ProductGallery name="Face Napkin" image="https://cdn.example.com/print-a.jpg" />,
    );

    const hero = container.querySelector('img[alt="Face Napkin"]');
    expect(hero).toBeTruthy();
    Object.defineProperty(hero, "naturalWidth", { value: 1000, configurable: true });
    Object.defineProperty(hero, "naturalHeight", { value: 800, configurable: true });
    fireEvent.load(hero!);

    expect(container.innerHTML).toContain("padding-bottom");

    rerender(<ProductGallery name="Face Napkin" image="https://cdn.example.com/print-b.jpg" />);

    expect(container.querySelectorAll('img[alt="Face Napkin"]')).toHaveLength(1);
    expect(container.innerHTML).toContain("padding-bottom");
    expect(container.querySelector('img[alt="Face Napkin"]')).toHaveClass("absolute", "inset-0", "h-full", "w-full");
  });

  it("prefetches only the next and previous shots after the hero loads", () => {
    const spy = spyHtmlImage();
    try {
      const { container } = render(
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

      const hero = container.querySelector('img[alt="Fleece vest"]');
      expect(hero).toBeTruthy();
      Object.defineProperty(hero, "naturalWidth", { value: 800, configurable: true });
      Object.defineProperty(hero, "naturalHeight", { value: 1000, configurable: true });
      fireEvent.load(hero!);

      expect(spy.created.filter(Boolean)).toEqual([
        "https://cdn.example.com/front.jpg.display.jpg",
        "https://cdn.example.com/lifestyle.jpg.display.jpg",
      ]);
    } finally {
      spy.restore();
    }
  });

  it("lazy-loads side thumbs beyond the first visible rail", () => {
    const { getAllByRole } = render(
      <ProductGallery
        name="Fleece vest"
        image="https://cdn.example.com/hero.jpg"
        gallery={[
          "https://cdn.example.com/2.jpg",
          "https://cdn.example.com/3.jpg",
          "https://cdn.example.com/4.jpg",
          "https://cdn.example.com/5.jpg",
          "https://cdn.example.com/6.jpg",
          "https://cdn.example.com/7.jpg",
          "https://cdn.example.com/8.jpg",
        ]}
      />,
    );

    const thumbs = getAllByRole("button").filter((el) =>
      (el.getAttribute("aria-label") ?? "").startsWith("View image"),
    );
    expect(thumbs).toHaveLength(8);
    expect(thumbs[5]?.querySelector("img")).toHaveAttribute("loading", "eager");
    expect(thumbs[6]?.querySelector("img")).toHaveAttribute("loading", "lazy");
    expect(thumbs[7]?.querySelector("img")).toHaveAttribute("loading", "lazy");
  });
});
