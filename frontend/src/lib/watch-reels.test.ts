import { describe, expect, it } from "vitest";
import type { Product } from "@/data/products";
import {
  HOME_WATCH_SHOP_LIMIT,
  getWatchReelValidationErrors,
  matchReelsToProducts,
  normalizeWatchReelDrafts,
  parseWatchReels,
  resolveWatchShopReels,
  validateWatchReelDrafts,
  watchReelDraftsFromPayload,
  watchReelsToPayload,
} from "./watch-reels";

const product = (patch: Partial<Product> & { slug: string; name?: string }): Product =>
  ({
    id: patch.slug,
    name: patch.name ?? patch.slug,
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
    ...patch,
  }) as Product;

describe("watch-reels", () => {
  it("reads video, sku, and product_slug from the home section payload", () => {
    expect(
      watchReelDraftsFromPayload({
        reels: [
          { video: "https://cdn.example/b.mp4", sku: "MNM-B", product_slug: "b", sort_order: 2 },
          { video_url: "https://cdn.example/a.mp4", product_sku: "MNM-A", slug: "a", sort_order: 1 },
        ],
      }),
    ).toEqual([
      { video: "https://cdn.example/a.mp4", sku: "MNM-A", product_slug: "a", sort_order: 1 },
      { video: "https://cdn.example/b.mp4", sku: "MNM-B", product_slug: "b", sort_order: 2 },
    ]);
  });

  it("parses CMS reels and drops lovable placeholders", () => {
    expect(
      parseWatchReels({
        reels: [
          { video: "https://cdn.example/a.mp4", sku: "MNM-A", product_slug: "a" },
          { video: "/__l5e/assets-v1/old.mp4", sku: "MNM-B", product_slug: "b" },
          { video: "https://cdn.example/c.mp4", sku: "", product_slug: "" },
        ],
      }),
    ).toEqual([{ video: "https://cdn.example/a.mp4", slug: "a", sku: "MNM-A" }]);
  });

  it("matches reels to products by parent SKU, variant SKU, then slug", () => {
    const romper = product({
      slug: "terracotta-petal-romper",
      sku: "MNM-R-001",
      variants: [{ sku: "MNM-R-001-03", size: "0-3m", color: "Brick", price: 100, stock: 2, active: true }],
    });
    const swaddle = product({ slug: "cloud-muslin-swaddle", sku: "MNM-SW-001" });
    const matched = matchReelsToProducts(
      [
        { video: "/a.mp4", slug: "", sku: "MNM-R-001-03" },
        { video: "/b.mp4", slug: "cloud-muslin-swaddle", sku: "" },
      ],
      [romper, swaddle],
    );
    expect(matched.map((r) => r.product.slug)).toEqual([
      "terracotta-petal-romper",
      "cloud-muslin-swaddle",
    ]);
  });

  it("caps saved reels at the homepage limit", () => {
    const drafts = Array.from({ length: 6 }, (_, i) => ({
      video: `https://cdn.example/${i}.mp4`,
      sku: `SKU-${i}`,
      product_slug: `p-${i}`,
      sort_order: i,
    }));
    expect(normalizeWatchReelDrafts(drafts)).toHaveLength(HOME_WATCH_SHOP_LIMIT);
    expect(validateWatchReelDrafts(drafts)).toEqual([
      `Add at most ${HOME_WATCH_SHOP_LIMIT} reels — the homepage shows five per section.`,
    ]);
  });

  it("requires a video link and a SKU or product", () => {
    expect(getWatchReelValidationErrors({ video: "", sku: "", product_slug: "", sort_order: 0 })).toEqual(
      expect.arrayContaining(["Video link is required.", "Pick a product SKU."]),
    );
    expect(
      getWatchReelValidationErrors({
        video: "https://cdn.example/reel.mp4",
        sku: "MNM-A",
        product_slug: "a",
        sort_order: 0,
      }),
    ).toEqual([]);
  });

  it("serializes admin drafts back to payload rows", () => {
    expect(
      watchReelsToPayload([
        { video: " https://cdn.example/a.mp4 ", sku: " MNM-A ", product_slug: "A", sort_order: 1 },
        { video: "", sku: "", product_slug: "", sort_order: 2 },
      ]),
    ).toEqual([{ video: "https://cdn.example/a.mp4", sku: "MNM-A", product_slug: "a", sort_order: 1 }]);
  });

  it("resolves only catalog-matched reels", () => {
    const live = [product({ slug: "cloud-muslin-swaddle", sku: "MNM-SW-001" })];
    expect(
      resolveWatchShopReels([{ video: "/a.mp4", slug: "missing", sku: "NOPE" }], live),
    ).toEqual([]);
    expect(
      resolveWatchShopReels([{ video: "/a.mp4", slug: "", sku: "MNM-SW-001" }], live).map(
        (r) => r.product.slug,
      ),
    ).toEqual(["cloud-muslin-swaddle"]);
  });
});
