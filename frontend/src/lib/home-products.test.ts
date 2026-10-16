import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import {
  HOME_SECTION_PRODUCT_KEYS,
  HOME_SECTION_PRODUCT_LIMIT,
  LOVED_RIGHT_NOW_KEY,
  LOVED_RIGHT_NOW_LIMIT,
  MERCHANDISE_PRODUCT_KEYS,
  YOU_MAY_ALSO_LOVE_KEY,
  YOU_MAY_ALSO_LOVE_LIMIT,
  getHomeSectionProductSlugErrors,
  isMerchandiseProductKey,
  normalizeHomeSectionProductSlugs,
  productLimitForSection,
  pruneHomeSectionProductSlugs,
  resolveHomeSectionProducts,
  merchandisingProductLabel,
  resolveLovedRightNow,
  resolveYouMayAlsoLove,
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
  it("includes gifts in product section keys", () => {
    expect(HOME_SECTION_PRODUCT_KEYS).toContain("gifts");
  });

  it("treats shop menu and PDP pairing slots as merchandising pickers", () => {
    expect(MERCHANDISE_PRODUCT_KEYS).toEqual(
      expect.arrayContaining([LOVED_RIGHT_NOW_KEY, YOU_MAY_ALSO_LOVE_KEY, "gifts"]),
    );
    expect(isMerchandiseProductKey(LOVED_RIGHT_NOW_KEY)).toBe(true);
    expect(productLimitForSection(LOVED_RIGHT_NOW_KEY)).toBe(LOVED_RIGHT_NOW_LIMIT);
    expect(productLimitForSection(YOU_MAY_ALSO_LOVE_KEY)).toBe(YOU_MAY_ALSO_LOVE_LIMIT);
    expect(productLimitForSection("best_sellers")).toBe(HOME_SECTION_PRODUCT_LIMIT);
  });

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
      `Pick at most ${HOME_SECTION_PRODUCT_LIMIT} products for this section.`,
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

  it("resolves Loved Right Now from admin picks with a 3-item cap", () => {
    const all = ["a", "b", "c", "d"].map((slug) => sample(slug, { isBestseller: true }));
    expect(resolveLovedRightNow(all, ["d", "a", "c", "b"]).map((p) => p.slug)).toEqual([
      "d",
      "a",
      "c",
    ]);
  });

  it("auto-picks flagged products for Loved Right Now when no slugs are set", () => {
    const all = [
      sample("plain"),
      sample("new", { isNew: true }),
      sample("best", { isBestseller: true }),
      sample("trend", { isTrending: true }),
    ];
    expect(resolveLovedRightNow(all, []).map((p) => p.slug)).toEqual(["best", "new", "trend"]);
  });

  it("resolves PDP pairings from admin picks and skips the current product", () => {
    const all = ["a", "b", "c", "d", "e"].map((slug) => sample(slug, { category: "Infant" }));
    const current = all[0];
    expect(resolveYouMayAlsoLove(all, current, ["a", "e", "c"]).map((p) => p.slug)).toEqual([
      "e",
      "c",
      "b",
      "d",
    ]);
  });

  it("uses admin product_label over the catalog category for Loved Right Now", () => {
    const product = sample("shorts", { category: "Newborn", categoryName: "Jabla" });
    expect(merchandisingProductLabel({}, product)).toBe("Newborn");
    expect(merchandisingProductLabel({ product_label: "  kedar  " }, product)).toBe("kedar");
    expect(merchandisingProductLabel({ product_label: "   " }, product)).toBe("Newborn");
    expect(merchandisingProductLabel({}, sample("plain", { category: "Toddler" }))).toBe("Toddler");
  });

  it("falls back to same-category pieces when no PDP pairing slugs are set", () => {
    const current = sample("a", { category: "Infant", fabric: "Knit" });
    const all = [
      current,
      sample("b", { category: "Toddler", fabric: "Linen" }),
      sample("c", { category: "Infant", fabric: "Muslin" }),
      sample("d", { category: "Newborn", fabric: "Knit" }),
    ];
    expect(resolveYouMayAlsoLove(all, current, []).map((p) => p.slug)).toEqual(["c", "d", "b"]);
  });
});
