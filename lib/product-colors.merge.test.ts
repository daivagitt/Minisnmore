import { describe, expect, it } from "vitest";
import {
  mergeColorImagesIntoColors,
  collectStorefrontCardImages,
  buildColorImagesJson,
  normalizeStorefrontColors,
  resolveStorefrontProductImages,
  swatchImageForColor,
} from "@/lib/product-colors";

describe("mergeColorImagesIntoColors", () => {
  it("merges colorImagesJson galleries into string colour names", () => {
    const merged = mergeColorImagesIntoColors(
      ["AQUA & BLUE", "SNAIL & AQUA"],
      {
        "AQUA & BLUE": ["https://cdn.example.com/aqua-1.jpg", "https://cdn.example.com/aqua-2.jpg"],
        "SNAIL & AQUA": ["https://cdn.example.com/snail-1.jpg"],
      },
    );

    expect(merged).toHaveLength(2);
    expect(merged[0]).toMatchObject({
      name: "AQUA & BLUE",
      imageUrls: ["https://cdn.example.com/aqua-1.jpg", "https://cdn.example.com/aqua-2.jpg"],
    });
    expect(swatchImageForColor(normalizeStorefrontColors(merged)[0])).toBe(
      "https://cdn.example.com/aqua-1.jpg",
    );
  });

  it("matches gallery keys case-insensitively", () => {
    const merged = mergeColorImagesIntoColors(["Bird"], { bird: ["https://cdn.example.com/bird.jpg"] });
    expect(merged[0]?.imageUrls).toEqual(["https://cdn.example.com/bird.jpg"]);
  });
});

describe("resolveStorefrontProductImages", () => {
  it("does not mix every print into product.gallery when per-print galleries exist", () => {
    const colors = mergeColorImagesIntoColors(
      ["Print A", "Print B"],
      {
        "Print A": ["https://cdn.example.com/a.jpg"],
        "Print B": ["https://cdn.example.com/b.jpg"],
      },
    );
    const result = resolveStorefrontProductImages(
      ["https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"],
      colors,
    );
    expect(result.image).toBe("https://cdn.example.com/a.jpg");
    expect(result.gallery).toEqual([]);
  });
  it("realigns generic print slots to colorImagesJson keys by position", () => {
    const merged = mergeColorImagesIntoColors(
      ["Print 3", "Print 4", "Print 5"],
      {
        "BROWN SWADDLE": ["https://cdn.example.com/brown.jpg"],
        "GREEN SWADDLE": ["https://cdn.example.com/green.jpg"],
        "WHITE SWADDLE": ["https://cdn.example.com/white.jpg"],
      },
    );

    expect(merged.map((e) => e.name)).toEqual(["BROWN SWADDLE", "GREEN SWADDLE", "WHITE SWADDLE"]);
    expect(merged[0]?.imageUrls).toEqual(["https://cdn.example.com/brown.jpg"]);
  });

  it("realigns generic print slots even when colours already have gallery URLs", () => {
    const merged = mergeColorImagesIntoColors(
      [
        { name: "Print-1", imageUrls: ["https://cdn.example.com/brown.jpg"] },
        { name: "Print-2", imageUrls: ["https://cdn.example.com/green.jpg"] },
      ],
      {
        "BROWN SWADDLE": ["https://cdn.example.com/brown.jpg"],
        "GREEN SWADDLE": ["https://cdn.example.com/green.jpg"],
      },
    );

    expect(merged.map((e) => e.name)).toEqual(["BROWN SWADDLE", "GREEN SWADDLE"]);
  });
});

describe("buildColorImagesJson", () => {
  it("maps gallery URLs per print and excludes swatches", () => {
    const json = buildColorImagesJson([
      {
        name: "SNAIL",
        swatchUrl: "https://cdn.example.com/swatch.jpg",
        imageUrls: ["https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"],
      },
    ]);
    expect(JSON.parse(json!)).toEqual({
      SNAIL: ["https://cdn.example.com/a.jpg", "https://cdn.example.com/b.jpg"],
    });
  });
});

describe("collectStorefrontCardImages", () => {
  it("uses gallery URLs and only one swatch when galleries are empty", () => {
    const urls = collectStorefrontCardImages([
      { name: "BEE", swatchUrl: "https://cdn.example.com/bee.jpg" },
      { name: "Navy", imageUrls: ["https://cdn.example.com/navy-1.jpg"] },
    ]);
    expect(urls).toEqual(["https://cdn.example.com/navy-1.jpg"]);
  });

  it("uses the first swatch when no galleries exist", () => {
    const urls = collectStorefrontCardImages([
      { name: "BEE", swatchUrl: "https://cdn.example.com/bee.jpg" },
      { name: "BLUE", swatchUrl: "https://cdn.example.com/blue.jpg" },
    ]);
    expect(urls).toEqual(["https://cdn.example.com/bee.jpg"]);
  });
});
