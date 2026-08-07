import { describe, expect, it } from "vitest";
import {
  PRODUCT_GALLERY_EXPORT_PX,
  PRODUCT_SWATCH_EXPORT_PX,
  fitWithinMaxDimension,
} from "./product-image-normalize";

describe("product-image-normalize", () => {
  it("scales landscape photos to max width", () => {
    const rect = fitWithinMaxDimension(1600, 900, PRODUCT_GALLERY_EXPORT_PX);
    expect(rect.drawW).toBe(PRODUCT_GALLERY_EXPORT_PX);
    expect(rect.drawH).toBe(675);
  });

  it("scales portrait photos to max height", () => {
    const rect = fitWithinMaxDimension(900, 1600, PRODUCT_GALLERY_EXPORT_PX);
    expect(rect.drawH).toBe(PRODUCT_GALLERY_EXPORT_PX);
    expect(rect.drawW).toBe(675);
  });

  it("never upscales small sources", () => {
    const rect = fitWithinMaxDimension(400, 300, PRODUCT_GALLERY_EXPORT_PX);
    expect(rect.drawW).toBe(400);
    expect(rect.drawH).toBe(300);
  });

  it("fits swatches within a smaller square cap", () => {
    const rect = fitWithinMaxDimension(800, 800, PRODUCT_SWATCH_EXPORT_PX);
    expect(rect.drawW).toBe(PRODUCT_SWATCH_EXPORT_PX);
    expect(rect.drawH).toBe(PRODUCT_SWATCH_EXPORT_PX);
    expect(PRODUCT_SWATCH_EXPORT_PX).toBeLessThan(PRODUCT_GALLERY_EXPORT_PX);
  });

  it("rejects zero-dimension sources", () => {
    expect(() => fitWithinMaxDimension(0, 100, 1200)).toThrow("no dimensions");
  });
});
