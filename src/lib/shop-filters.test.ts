import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import {
  buildShopSearch,
  filterAndSortProducts,
  filterProducts,
  parseShopSearch,
  productMatchesCategory,
  slugify,
} from "./shop-filters";

function makeProduct(overrides: Partial<Product> & Pick<Product, "id" | "slug">): Product {
  return {
    id: overrides.id,
    slug: overrides.slug,
    name: "Test",
    tagline: "",
    price: 100,
    image: "",
    gallery: [],
    ages: ["0-3m"],
    fabric: "Muslin",
    colors: [],
    sizes: ["One Size"],
    rating: 0,
    reviewCount: 0,
    category: "Newborn",
    categorySlug: "swaddles",
    description: "",
    ...overrides,
  };
}

describe("parseShopSearch", () => {
  it("accepts valid filters and defaults sort to new", () => {
    expect(parseShopSearch({ category: "dresses", age: "3-6m", fabric: "Muslin", sort: "lo" })).toEqual({
      category: "dresses",
      age: "3-6m",
      fabric: "Muslin",
      sort: "lo",
    });
  });

  it("drops invalid age, fabric, and sort values", () => {
    expect(parseShopSearch({ age: "bad", fabric: "Silk", sort: "nope", category: "  rompers  " })).toEqual({
      category: "rompers",
      sort: "new",
    });
  });
});

describe("buildShopSearch", () => {
  it("omits empty filter keys for clean URLs", () => {
    expect(buildShopSearch({ category: "", age: undefined, fabric: undefined, sort: "hi" })).toEqual({
      sort: "hi",
    });
  });
});

describe("shop product filters", () => {
  const products = [
    makeProduct({ id: "1", slug: "a", categorySlug: "swaddles", ages: ["0-3m"], fabric: "Muslin", price: 200 }),
    makeProduct({ id: "2", slug: "b", categorySlug: "dresses", ages: ["6-12m"], fabric: "Organic Cotton", price: 100 }),
    makeProduct({ id: "3", slug: "c", categorySlug: "swaddles", ages: ["3-6m"], fabric: "Muslin", price: 300, isNew: true }),
  ];

  it("filters by category slug case-insensitively", () => {
    expect(filterProducts(products, { category: "Swaddles", sort: "new" }).map((p) => p.slug)).toEqual(["a", "c"]);
  });

  it("falls back to category label when slug is missing", () => {
    const p = makeProduct({ id: "4", slug: "d", categorySlug: undefined, category: "Infant" });
    expect(productMatchesCategory(p, "infant")).toBe(true);
    expect(slugify("Infant")).toBe("infant");
  });

  it("matches by categoryName when slug is missing", () => {
    const p = makeProduct({
      id: "5",
      slug: "e",
      categorySlug: undefined,
      categoryName: "Gift Sets",
      category: "Newborn",
    });
    expect(productMatchesCategory(p, "gift-sets")).toBe(true);
  });

  it("matches category slugs when hyphens differ (t-shirt vs tshirt)", () => {
    const p = makeProduct({
      id: "6",
      slug: "tee",
      categorySlug: "t-shirt",
      categoryName: "T-Shirt",
      category: "Newborn",
    });
    expect(productMatchesCategory(p, "tshirt")).toBe(true);
    expect(productMatchesCategory(p, "t-shirt")).toBe(true);
  });

  it("filters by age and fabric together", () => {
    expect(filterProducts(products, { age: "3-6m", fabric: "Muslin", sort: "new" }).map((p) => p.slug)).toEqual(["c"]);
  });

  it("excludes products that do not offer the selected size band", () => {
    const sized = [
      makeProduct({ id: "7", slug: "socks", categorySlug: "accessories", ages: ["0-3m", "3-6m", "6-12m"] }),
      makeProduct({ id: "8", slug: "vest", categorySlug: "vest", ages: ["12-18m", "18-24m"] }),
    ];
    expect(filterProducts(sized, { age: "0-3m", sort: "new" }).map((p) => p.slug)).toEqual(["socks"]);
    expect(filterProducts(sized, { age: "18-24m", sort: "new" }).map((p) => p.slug)).toEqual(["vest"]);
  });

  it("sorts by price low to high", () => {
    expect(filterAndSortProducts(products, { sort: "lo" }).map((p) => p.slug)).toEqual(["b", "a", "c"]);
  });

  it("sorts by price high to low", () => {
    expect(filterAndSortProducts(products, { sort: "hi" }).map((p) => p.slug)).toEqual(["c", "a", "b"]);
  });
});
