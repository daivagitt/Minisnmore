import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import { sortBestsellersFirst, sortBySortOrder, sortNewArrivalsFirst } from "@/lib/product-sort";

const p = (slug: string, sortOrder: number, flags: Partial<Product> = {}): Product =>
  ({
    id: slug,
    slug,
    name: slug,
    tagline: "",
    price: 100,
    image: "/a.jpg",
    gallery: ["/a.jpg"],
    ages: ["0-3m"],
    fabric: "Organic Cotton",
    colors: [],
    sizes: ["One Size"],
    rating: 0,
    reviewCount: 0,
    category: "Newborn",
    description: "",
    sortOrder,
    ...flags,
  }) as Product;

describe("product-sort", () => {
  it("sorts by catalog sort order", () => {
    const sorted = sortBySortOrder([p("c", 30), p("a", 10), p("b", 20)]);
    expect(sorted.map((x) => x.slug)).toEqual(["a", "b", "c"]);
  });

  it("puts bestsellers ahead of other products", () => {
    const sorted = sortBestsellersFirst([
      p("regular-high", 1),
      p("best-low", 99, { isBestseller: true }),
      p("regular-low", 2),
    ]);
    expect(sorted.map((x) => x.slug)).toEqual(["best-low", "regular-high", "regular-low"]);
  });

  it("puts new arrivals ahead of other products", () => {
    const sorted = sortNewArrivalsFirst([
      p("regular-high", 1),
      p("new-low", 99, { isNew: true }),
      p("regular-low", 2),
    ]);
    expect(sorted.map((x) => x.slug)).toEqual(["new-low", "regular-high", "regular-low"]);
  });
});
