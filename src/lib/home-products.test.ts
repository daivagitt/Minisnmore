import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import {
  HOME_SECTION_PRODUCT_LIMIT,
  getHomeSectionProductSlugErrors,
  normalizeHomeSectionProductSlugs,
  pruneHomeSectionProductSlugs,
  resolveHomeSectionProducts,
  sectionProductSlugs,
} from "./home-products";

const sample = (slug: string, flags: Partial<Product> = {}): Product =>
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
    ...flags,
  }) as Product;

describe("home-products", () => {
  it("reads product slugs from section payload", () => {
    expect(sectionProductSlugs({ product_slugs: ["a", "", 1, "b"] })).toEqual(["a", "b"]);
    expect(sectionProductSlugs({})).toEqual([]);
  });

  it("uses admin-picked slugs when provided", () => {
    const all = [sample("a", { isBestseller: true }), sample("b"), sample("c", { isBestseller: true })];
    const picked = resolveHomeSectionProducts(all, ["c", "missing", "a"], (ps) => ps.filter((p) => p.isBestseller));
    expect(picked.map((p) => p.slug)).toEqual(["c", "a"]);
  });

  it("caps admin-picked slugs at the homepage limit", () => {
    const slugs = ["a", "b", "c", "d", "e", "f"];
    const capped = slugs.slice(0, HOME_SECTION_PRODUCT_LIMIT);
    expect(normalizeHomeSectionProductSlugs(slugs)).toEqual(capped);
    expect(getHomeSectionProductSlugErrors(slugs)).toEqual([
      `Pick at most ${HOME_SECTION_PRODUCT_LIMIT} products — the homepage shows five per section.`,
    ]);
    expect(sectionProductSlugs({ product_slugs: slugs })).toEqual(capped);
  });

  it("limits resolved products on the storefront", () => {
    const slugs = ["a", "b", "c", "d", "e", "f"];
    const all = slugs.map((slug) => sample(slug));
    const picked = resolveHomeSectionProducts(all, [...slugs].reverse(), (ps) => ps);
    expect(picked).toHaveLength(HOME_SECTION_PRODUCT_LIMIT);
    expect(picked.map((p) => p.slug)).toEqual(
      [...slugs].reverse().slice(0, HOME_SECTION_PRODUCT_LIMIT),
    );
  });

  it("prunes stale product slugs that are not in the catalog", () => {
    const known = ["a", "b", "c", "d"];
    expect(pruneHomeSectionProductSlugs(["a", "missing", "b", "c", "d", "ghost"], known)).toEqual([
      "a",
      "b",
      "c",
      "d",
    ]);
  });

  it("falls back to flag-based selection when no slugs are set", () => {
    const all = [
      sample("a", { isBestseller: true, sortOrder: 20 }),
      sample("b"),
      sample("c", { isBestseller: true, sortOrder: 5 }),
    ];
    const auto = resolveHomeSectionProducts(all, [], (ps) => ps.filter((p) => p.isBestseller));
    expect(auto.map((p) => p.slug)).toEqual(["c", "a"]);
  });
});
