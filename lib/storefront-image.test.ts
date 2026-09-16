import { describe, expect, it } from "vitest";
import {
  STOREFRONT_CARD_SIZES,
  STOREFRONT_HERO_SIZES,
  STOREFRONT_IMAGE_DISPLAY_WIDTH,
  STOREFRONT_IMAGE_ORIGINAL_WIDTH,
  storefrontImageSrcSet,
  storefrontImageUrl,
} from "./storefront-image";

describe("storefrontImageUrl", () => {
  it("rewrites https catalog URLs to thumb and display siblings", () => {
    const original = "https://cdn.example.com/products/romper.jpg";
    expect(storefrontImageUrl(original, "original")).toBe(original);
    expect(storefrontImageUrl(original, "thumb")).toBe(`${original}.thumb.jpg`);
    expect(storefrontImageUrl(original, "display")).toBe(`${original}.display.jpg`);
  });

  it("does not double-append when the URL is already a derivative", () => {
    const original = "https://cdn.example.com/products/romper.jpg";
    expect(storefrontImageUrl(`${original}.thumb.jpg`, "display")).toBe(`${original}.display.jpg`);
    expect(storefrontImageUrl(`${original}.display.jpg`, "thumb")).toBe(`${original}.thumb.jpg`);
    expect(storefrontImageUrl(`${original}.thumb.jpg`, "original")).toBe(original);
  });

  it("leaves blob, data, and site-relative paths unchanged", () => {
    expect(storefrontImageUrl("blob:https://minisnmore.com/1", "thumb")).toBe("blob:https://minisnmore.com/1");
    expect(storefrontImageUrl("data:image/png;base64,abc", "display")).toBe("data:image/png;base64,abc");
    expect(storefrontImageUrl("/uploads/local.jpg", "thumb")).toBe("/uploads/local.jpg");
  });

  it("returns empty for blank values", () => {
    expect(storefrontImageUrl("")).toBe("");
    expect(storefrontImageUrl("   ", "thumb")).toBe("");
    expect(storefrontImageUrl(null, "display")).toBe("");
  });

  it("keeps query and hash after the derivative suffix", () => {
    expect(storefrontImageUrl("https://cdn.example.com/a.jpg?v=1#x", "thumb")).toBe(
      "https://cdn.example.com/a.jpg.thumb.jpg?v=1#x",
    );
  });
});

describe("storefrontImageSrcSet", () => {
  it("lists display then original widths", () => {
    const original = "https://cdn.example.com/hero.jpg";
    expect(storefrontImageSrcSet(original)).toBe(
      `${original}.display.jpg ${STOREFRONT_IMAGE_DISPLAY_WIDTH}w, ${original} ${STOREFRONT_IMAGE_ORIGINAL_WIDTH}w`,
    );
    expect(STOREFRONT_HERO_SIZES).toContain("1250px");
    expect(STOREFRONT_CARD_SIZES).toContain("280px");
  });

  it("is empty for non-catalog URLs", () => {
    expect(storefrontImageSrcSet("/local.jpg")).toBe("");
    expect(storefrontImageSrcSet("")).toBe("");
  });
});
