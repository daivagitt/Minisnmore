import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import { filterProductsByQuery, productMatchesQuery } from "./product-search";

const product = (overrides: Partial<Product> = {}): Product => ({
  id: "1",
  slug: "muslin-swaddle",
  name: "Cloud Muslin Swaddle",
  tagline: "Breathable muslin wrap",
  price: 899,
  image: "/a.jpg",
  gallery: ["/a.jpg"],
  ages: ["0-3m"],
  fabric: "Muslin",
  colors: [],
  sizes: ["One Size"],
  rating: 0,
  reviewCount: 0,
  category: "Newborn",
  categorySlug: "swaddles",
  categoryName: "Swaddles",
  sku: "MNM-SW-001",
  description: "Soft organic muslin for newborn naps.",
  ...overrides,
});

describe("product-search", () => {
  it("matches name, slug, description, category, and sku", () => {
    const p = product();
    expect(productMatchesQuery(p, "muslin")).toBe(true);
    expect(productMatchesQuery(p, "swaddles")).toBe(true);
    expect(productMatchesQuery(p, "MNM-SW")).toBe(true);
    expect(productMatchesQuery(p, "romper")).toBe(false);
  });

  it("filters a product list", () => {
    const list = [product(), product({ id: "2", slug: "romper", name: "Cocoa Romper", sku: "MNM-R-1" })];
    expect(filterProductsByQuery(list, "romper")).toHaveLength(1);
  });
});
