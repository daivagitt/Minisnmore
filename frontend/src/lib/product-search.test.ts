import type { Product } from "@/data/products";
import { describe, expect, it } from "vitest";
import {
  adminProductMatchesQuery,
  filterProductsByQuery,
  normalizeSearchQuery,
  productMatchesQuery,
  rankProductMatch,
  searchTokens,
} from "./product-search";
import type { ProductRow } from "./backend-data";

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
  colors: [{ name: "Cream", hex: "#fff" }],
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
  it("normalizes and caps query length", () => {
    expect(normalizeSearchQuery("  Hello  ")).toBe("hello");
    expect(normalizeSearchQuery("x".repeat(200)).length).toBe(100);
  });

  it("tokenizes on whitespace", () => {
    expect(searchTokens("  Cloud   Muslin ")).toEqual(["cloud", "muslin"]);
  });

  it("matches name, slug, description, category, and sku", () => {
    const p = product();
    expect(productMatchesQuery(p, "muslin")).toBe(true);
    expect(productMatchesQuery(p, "swaddles")).toBe(true);
    expect(productMatchesQuery(p, "MNM-SW")).toBe(true);
    expect(productMatchesQuery(p, "romper")).toBe(false);
  });

  it("requires every token to match", () => {
    const p = product();
    expect(productMatchesQuery(p, "cloud muslin")).toBe(true);
    expect(productMatchesQuery(p, "cloud romper")).toBe(false);
  });

  it("matches variant sku, size, and color", () => {
    const p = product({
      itemStructure: "VARIANTS",
      variants: [
        { sku: "HORIZON-M-NAVY", size: "M", color: "Navy", price: 800, stock: 2, active: true },
      ],
    });
    expect(productMatchesQuery(p, "HORIZON-M")).toBe(true);
    expect(productMatchesQuery(p, "navy")).toBe(true);
    expect(productMatchesQuery(p, "xl")).toBe(false);
  });

  it("ranks exact and prefix matches higher", () => {
    const exact = product({ id: "e", name: "Romper", sku: "ROMPER" });
    const partial = product({ id: "p", name: "Cocoa Romper Set", sku: "MNM-R-1" });
    expect(rankProductMatch(exact, "romper")).toBeGreaterThan(rankProductMatch(partial, "romper"));
  });

  it("filters and ranks a product list", () => {
    const list = [
      product(),
      product({ id: "2", slug: "romper", name: "Cocoa Romper", sku: "MNM-R-1" }),
      product({
        id: "3",
        slug: "horizon",
        name: "Horizon Tee",
        sku: "HORIZON",
        variants: [{ sku: "HORIZON-M-NAVY", size: "M", color: "Navy", price: 1, stock: 1, active: true }],
      }),
    ];
    expect(filterProductsByQuery(list, "romper").map((p) => p.id)).toEqual(["2"]);
    expect(filterProductsByQuery(list, "horizon-m-navy", { limit: 1 })[0]?.id).toBe("3");
  });

  it("matches admin rows including variant skus in variants_json", () => {
    const row = {
      slug: "horizon-tee",
      name: "Horizon Tee",
      sku: "HORIZON",
      fabric_type: "Knit",
      description: null,
      story: null,
      category_id: null,
      category_slug: "tees",
      category_name: "Tees",
      price: 100,
      compare_at_price: 125,
      currency: "INR",
      age_min_months: 3,
      age_max_months: 12,
      sizes: ["M"],
      colors: [{ name: "Navy" }],
      item_structure: "VARIANTS",
      variants_json: JSON.stringify([{ sku: "HORIZON-M-NAVY", size: "M", color: "Navy", rate: 125, stock: 2 }]),
      images: [],
      badge: null,
      units: null,
      hsn: null,
      tax_pct: null,
      stock: 2,
      sort_order: 0,
      featured: false,
      trending: false,
      new_arrival: false,
      best_seller: false,
      active: true,
    } satisfies ProductRow;

    expect(adminProductMatchesQuery(row, "horizon-m-navy")).toBe(true);
    expect(adminProductMatchesQuery(row, "missing")).toBe(false);
  });
});
