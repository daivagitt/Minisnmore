import { describe, expect, it } from "vitest";
import {
  boundsFromBackgroundMask,
  contentAspect,
  detectContentBoundsFromImageData,
  imageFitStyles,
  isFullImageBounds,
  isProductImageBackground,
} from "./product-image-bounds";

describe("product-image-bounds", () => {
  it("treats near-white and surface pixels as background", () => {
    expect(isProductImageBackground(255, 255, 255, 255)).toBe(true);
    expect(isProductImageBackground(246, 246, 246, 255)).toBe(true);
    expect(isProductImageBackground(40, 80, 120, 255)).toBe(false);
  });

  it("finds content bounds in a synthetic mask", () => {
    const w = 10;
    const h = 10;
    const isBackground = (x: number, y: number) => x < 2 || x > 7 || y < 2 || y > 7;
    const bounds = boundsFromBackgroundMask(w, h, isBackground, 1000, 500);
    expect(bounds.x).toBeLessThanOrEqual(200);
    expect(bounds.y).toBeLessThanOrEqual(100);
    expect(bounds.width).toBeGreaterThanOrEqual(500);
    expect(bounds.height).toBeGreaterThanOrEqual(250);
    expect(contentAspect(bounds)).toBeGreaterThan(1);
  });

  it("returns full frame when every pixel is background", () => {
    const bounds = boundsFromBackgroundMask(4, 4, () => true, 800, 600);
    expect(bounds).toEqual({
      x: 0,
      y: 0,
      width: 800,
      height: 600,
      naturalWidth: 800,
      naturalHeight: 600,
    });
  });

  it("maps image styles so content fills the box", () => {
    const bounds = {
      x: 100,
      y: 50,
      width: 400,
      height: 200,
      naturalWidth: 800,
      naturalHeight: 400,
    };
    const styles = imageFitStyles(bounds);
    expect(styles.width).toBe("200%");
    expect(styles.height).toBe("200%");
    expect(styles.left).toBe("-25%");
    expect(styles.top).toBe("-25%");
  });

  it("detects full-frame bounds", () => {
    const bounds = {
      x: 0,
      y: 0,
      width: 800,
      height: 600,
      naturalWidth: 800,
      naturalHeight: 600,
    };
    expect(isFullImageBounds(bounds)).toBe(true);
  });

  it("detects bounds from centred content in image data", () => {
    const w = 20;
    const h = 10;
    const data = new Uint8ClampedArray(w * h * 4);
    for (let y = 0; y < h; y++) {
      for (let x = 0; x < w; x++) {
        const i = (y * w + x) * 4;
        const isContent = x >= 5 && x <= 14 && y >= 2 && y <= 7;
        const v = isContent ? 30 : 250;
        data[i] = v;
        data[i + 1] = v;
        data[i + 2] = v;
        data[i + 3] = 255;
      }
    }
    const bounds = detectContentBoundsFromImageData(data, w, h, 1000, 500);
    expect(bounds.width).toBeGreaterThan(400);
    expect(bounds.height).toBeGreaterThan(200);
    expect(bounds.x).toBeGreaterThan(0);
    expect(bounds.y).toBeGreaterThan(0);
    expect(isFullImageBounds(bounds)).toBe(false);
  });
});
